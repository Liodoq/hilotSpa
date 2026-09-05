import { Injectable, NgZone, inject, signal } from '@angular/core';

/**
 * Task 2.6 — "Virtual voice and subtitles for communicating with customers."
 *
 * This is a Scope bullet in the paper (p.10), not a nice-to-have, so it is
 * built on the browser's own Web Speech API: no key, no vendor, no cost, and
 * nothing to fail at the spa's bandwidth. SpeechSynthesis reads the assistant
 * out loud; SpeechRecognition takes the client's answer back.
 *
 * Two things this deliberately does NOT do:
 *
 *  - It never gates the conversation. Every browser that cannot speak or
 *    listen still has the typed path, and the screen says so rather than
 *    presenting a dead button. Safari and most Android browsers have no
 *    SpeechRecognition at all.
 *  - It never puts audio on the booking path. A recognised sentence is dropped
 *    into the SAME input box the client could have typed, and goes through the
 *    same Spring validation. Voice changes how a sentence arrives, never what
 *    the server is willing to do with it.
 *
 * The subtitle is a real caption, not an echo: `caption()` carries the sentence
 * currently being spoken plus how much of it has been said, so the band can
 * highlight the words as they are heard. That is what makes it usable by
 * someone who has the volume down or does not hear well — NFR#4.
 */

/* ------------------------------------------------------------------ typings
 * SpeechSynthesis is in TypeScript's DOM lib. SpeechRecognition is not — it is
 * still vendor-prefixed in every shipping browser — so the small part of its
 * shape we actually touch is declared here rather than adding a dependency. */
interface RecAlternative { readonly transcript: string; readonly confidence: number; }
interface RecResult { readonly length: number; readonly isFinal: boolean; [i: number]: RecAlternative; }
interface RecResultList { readonly length: number; [i: number]: RecResult; }
interface RecEvent extends Event { readonly resultIndex: number; readonly results: RecResultList; }
interface RecErrorEvent extends Event { readonly error: string; }
interface Recognition extends EventTarget {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  start(): void;
  stop(): void;
  abort(): void;
}
type RecognitionCtor = new () => Recognition;

/** The sentence being spoken, and how many characters of it have been said. */
export interface Caption { text: string; spoken: number; }

/** en-PH is the spa's working language; fil-PH is offered because clients
 *  code-switch (paper §Related Literature [35]). Recognition accepts both. */
export type SpeechLang = 'en-PH' | 'fil-PH';

const VOICE_KEY = 'hilotspa.voiceOn';
const LANG_KEY = 'hilotspa.speechLang';

/** Long utterances get truncated by Chrome, and a whole paragraph cannot be
 *  captioned. Both problems go away if we speak one sentence at a time. */
const MAX_CHUNK = 180;

function stored(key: string): string | null {
  try { return localStorage.getItem(key); } catch { return null; }
}
function store(key: string, value: string): void {
  try { localStorage.setItem(key, value); } catch { /* private mode */ }
}

/** The thread renders HTML (bold service names). A screen reader voice must
 *  not say "less than b greater than". */
function plain(html: string): string {
  return html
    .replace(/<br\s*\/?>/gi, '. ')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/₱\s*/g, 'PHP ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** One caption line per sentence, and never a line too long to read. */
function chunk(text: string): string[] {
  const out: string[] = [];
  for (const sentence of text.split(/(?<=[.!?…])\s+/)) {
    let rest = sentence.trim();
    if (!rest) continue;
    while (rest.length > MAX_CHUNK) {
      let cut = rest.lastIndexOf(' ', MAX_CHUNK);
      if (cut < 40) cut = MAX_CHUNK;
      out.push(rest.slice(0, cut).trim());
      rest = rest.slice(cut).trim();
    }
    if (rest) out.push(rest);
  }
  // "Ana R. · Room 2" ends a sentence at "R." as far as a regex is concerned.
  // Rather than teach it every abbreviation, glue any scrap back onto the line
  // before it: a caption that flashes "Ana R." on its own is worse than a
  // slightly long one.
  const merged: string[] = [];
  for (const line of out) {
    const prev = merged[merged.length - 1];
    if (prev !== undefined && line.length < 25 && prev.length + line.length <= MAX_CHUNK) {
      merged[merged.length - 1] = prev + ' ' + line;
    } else {
      merged.push(line);
    }
  }
  return merged;
}

@Injectable({ providedIn: 'root' })
export class SpeechService {
  private zone = inject(NgZone);

  /** Feature detection, done once. The UI asks these before drawing a button
   *  it cannot honour. */
  readonly canSpeak: boolean;
  readonly canListen: boolean;

  /** The client's own preference, remembered. Voice defaults ON where it is
   *  available: the paper promises a talking assistant, and a feature nobody
   *  discovers is the same as a feature nobody built. */
  readonly voiceOn = signal(false);
  readonly lang = signal<SpeechLang>('en-PH');

  readonly speaking = signal(false);
  readonly listening = signal(false);

  /** What the caption band shows while speaking. */
  readonly caption = signal<Caption>({ text: '', spoken: 0 });
  /** Words recognised so far this turn, shown live so the client can see they
   *  are being heard — and see a misheard word before it is sent. */
  readonly heard = signal('');
  /** Plain-language reason the microphone stopped. Empty when fine. */
  readonly micError = signal('');

  /** True while the client is being recorded for server-side transcription. */
  readonly recording = signal(false);

  /**
   * Every modern browser can capture audio, which is the whole point of this
   * second path: it works where SpeechRecognition does not exist, and it keeps
   * the audio inside the spa's own Google Cloud project rather than sending it
   * to the browser vendor's transcription service.
   */
  readonly canRecord =
    typeof navigator !== 'undefined'
    && !!navigator.mediaDevices?.getUserMedia
    && typeof MediaRecorder !== 'undefined';

  private queue: string[] = [];
  private current: SpeechSynthesisUtterance | null = null;
  private keepAlive: ReturnType<typeof setInterval> | null = null;
  private voices: SpeechSynthesisVoice[] = [];

  private recognition: Recognition | null = null;
  private onFinal: ((text: string) => void) | null = null;
  private finalText = '';

  constructor() {
    const w = typeof window === 'undefined' ? null : window;
    this.canSpeak = !!w && 'speechSynthesis' in w && 'SpeechSynthesisUtterance' in w;
    this.canListen = !!this.ctor();

    if (this.canSpeak) {
      this.loadVoices();
      // Chrome populates the voice list asynchronously; on a cold load
      // getVoices() is [] and the first line would be read in the wrong accent.
      window.speechSynthesis.addEventListener('voiceschanged', () => this.loadVoices());
    }

    this.voiceOn.set(this.canSpeak && stored(VOICE_KEY) !== 'off');
    const savedLang = stored(LANG_KEY);
    if (savedLang === 'fil-PH' || savedLang === 'en-PH') this.lang.set(savedLang);
  }

  // ------------------------------------------------------------- preferences

  toggleVoice(): boolean {
    const on = !this.voiceOn();
    this.voiceOn.set(on);
    store(VOICE_KEY, on ? 'on' : 'off');
    if (!on) this.stopSpeaking();
    return on;
  }

  /** One button, two languages. Recognition uses it immediately; synthesis
   *  uses it on the next line spoken. */
  toggleLang(): SpeechLang {
    const next: SpeechLang = this.lang() === 'en-PH' ? 'fil-PH' : 'en-PH';
    this.lang.set(next);
    store(LANG_KEY, next);
    if (this.listening()) { this.stopListening(); }
    return next;
  }

  // ------------------------------------------------------------------ speech

  /**
   * Queue a line to be read. Calls are additive on purpose: the assistant
   * opens with several lines in a row, and each should be heard in order
   * rather than cutting off the one before it.
   */
  speak(html: string): void {
    if (!this.canSpeak || !this.voiceOn()) return;
    const text = plain(html);
    if (!text) return;
    this.queue.push(...chunk(text));
    if (!this.speaking()) this.next();
  }

  /** Read one line again, from the beginning, ignoring whatever is queued. */
  replay(html: string): void {
    if (!this.canSpeak) return;
    if (!this.voiceOn()) this.toggleVoice();
    this.stopSpeaking();
    this.speak(html);
  }

  stopSpeaking(): void {
    if (!this.canSpeak) return;
    this.queue.length = 0;
    // Dropping the reference first makes every in-flight handler a no-op, so
    // the 'error' that cancel() raises cannot restart the queue.
    this.current = null;
    try { window.speechSynthesis.cancel(); } catch { /* nothing to cancel */ }
    this.finishSpeaking();
  }

  private next(): void {
    const line = this.queue.shift();
    if (line === undefined) { this.finishSpeaking(); return; }

    const u = new SpeechSynthesisUtterance(line);
    u.lang = this.lang();
    const voice = this.pickVoice();
    if (voice) u.voice = voice;
    // Slightly under normal pace. The spa's clients skew older and this is a
    // stated non-functional requirement (NFR#4), not a taste call.
    u.rate = 0.95;
    u.pitch = 1;
    u.volume = 1;

    u.addEventListener('start', () => this.zone.run(() => {
      if (this.current !== u) return;
      this.speaking.set(true);
      this.caption.set({ text: line, spoken: 0 });
    }));

    // Chrome reports where it is in the sentence. That is what turns the band
    // from an echo of the reply into a caption that tracks the voice.
    u.addEventListener('boundary', (ev: Event) => this.zone.run(() => {
      if (this.current !== u) return;
      const e = ev as SpeechSynthesisEvent;
      const to = Math.min(line.length, e.charIndex + (e.charLength || 0));
      this.caption.set({ text: line, spoken: to });
    }));

    u.addEventListener('end', () => this.zone.run(() => {
      if (this.current !== u) return;
      this.caption.set({ text: line, spoken: line.length });
      this.next();
    }));

    // A voice that fails must never take the conversation with it.
    u.addEventListener('error', () => this.zone.run(() => {
      if (this.current !== u) return;
      this.next();
    }));

    this.current = u;
    this.speaking.set(true);
    window.speechSynthesis.speak(u);
    this.startKeepAlive();
  }

  private finishSpeaking(): void {
    this.current = null;
    this.speaking.set(false);
    if (this.keepAlive) { clearInterval(this.keepAlive); this.keepAlive = null; }
  }

  /** Chrome suspends synthesis after roughly fifteen seconds. Sentence-sized
   *  utterances mostly avoid it; this covers the long ones. */
  private startKeepAlive(): void {
    if (this.keepAlive) return;
    this.keepAlive = setInterval(() => {
      if (!this.speaking()) return;
      try { window.speechSynthesis.resume(); } catch { /* ignore */ }
    }, 8000);
  }

  private loadVoices(): void {
    try { this.voices = window.speechSynthesis.getVoices(); } catch { this.voices = []; }
  }

  private pickVoice(): SpeechSynthesisVoice | null {
    if (!this.voices.length) this.loadVoices();
    const want = this.lang().toLowerCase();
    const head = want.slice(0, 2);
    const norm = (v: SpeechSynthesisVoice) => v.lang.replace('_', '-').toLowerCase();
    return this.voices.find(v => norm(v) === want)
      ?? this.voices.find(v => norm(v).startsWith(head))
      // No Filipino voice installed is the normal case on Windows. An English
      // voice reading Taglish is far better than silence.
      ?? this.voices.find(v => norm(v).startsWith('en'))
      ?? null;
  }

  // ---------------------------------------------------------------- listening

  private ctor(): RecognitionCtor | null {
    if (typeof window === 'undefined') return null;
    const w = window as unknown as {
      SpeechRecognition?: RecognitionCtor;
      webkitSpeechRecognition?: RecognitionCtor;
    };
    return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
  }

  /**
   * Start listening. `onFinal` receives the finished sentence once, after the
   * client stops talking — the caller decides what to do with it, which keeps
   * this service out of the booking flow entirely.
   *
   * Returns false when the browser cannot listen, so the caller can say so
   * instead of leaving a button that does nothing.
   */
  startListening(onFinal: (text: string) => void): boolean {
    const Ctor = this.ctor();
    if (!Ctor) return false;
    if (this.listening()) return true;

    // It would otherwise transcribe its own voice.
    this.stopSpeaking();

    const rec = new Ctor();
    rec.lang = this.lang();
    rec.continuous = false;      // one answer per press
    rec.interimResults = true;   // so the client can watch it being heard
    rec.maxAlternatives = 1;

    this.onFinal = onFinal;
    this.finalText = '';
    this.micError.set('');
    this.heard.set('');

    rec.addEventListener('result', (ev: Event) => this.zone.run(() => {
      const e = ev as RecEvent;
      let interim = '';
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const res = e.results[i];
        const said = res[0]?.transcript ?? '';
        if (res.isFinal) this.finalText += said; else interim += said;
      }
      this.heard.set((this.finalText + ' ' + interim).trim());
    }));

    rec.addEventListener('error', (ev: Event) => this.zone.run(() => {
      this.micError.set(this.explain((ev as RecErrorEvent).error));
    }));

    rec.addEventListener('end', () => this.zone.run(() => {
      this.listening.set(false);
      this.recognition = null;
      const said = this.finalText.trim();
      const done = this.onFinal;
      this.onFinal = null;
      this.finalText = '';
      if (said && done) done(said);
    }));

    try {
      rec.start();
    } catch {
      // start() throws if a previous session is still winding down.
      this.micError.set('The microphone is still busy. Please try again.');
      return true;
    }
    this.recognition = rec;
    this.listening.set(true);
    return true;
  }

  /** stop(), not abort() — stop still delivers whatever was already said. */
  stopListening(): void {
    if (!this.recognition) { this.listening.set(false); return; }
    try { this.recognition.stop(); } catch { /* already stopped */ }
  }

  private explain(code: string): string {
    switch (code) {
      case 'not-allowed':
      case 'service-not-allowed':
        return 'The microphone is blocked for this site. Allow it in your browser, or type instead.';
      case 'no-speech':
        return 'I did not hear anything. Tap the microphone and speak again, or type.';
      case 'audio-capture':
        return 'No microphone was found on this device. You can type instead.';
      case 'network':
        return 'Voice input needs a connection. You can type instead.';
      case 'aborted':
        return '';
      default:
        return 'Voice input stopped. You can type instead.';
    }
  }

  // ------------------------------------------------------------- recording
  /*
   * The second voice path: capture here, transcribe on the server.
   *
   * SpeechRecognition above is faster and can caption a sentence as it is
   * heard, so it stays the first choice where the browser has it. This path
   * exists for two reasons. Safari and Firefox have no SpeechRecognition, so
   * without it those clients have no microphone at all. And Chrome's
   * recognition sends the audio to Google's own service - a second route out of
   * the building that the project's data-handling argument (Vertex AI on Google
   * Cloud terms) never covered. Recording here and transcribing through n8n
   * keeps the audio in the same project, region and terms as the assistant.
   *
   * What comes back is TEXT, and it is put in the input box for the client to
   * read before they send it. Voice never becomes a second way into the
   * booking logic.
   */

  private media: MediaRecorder | null = null;
  private chunks: Blob[] = [];

  /** Ask for the microphone and start recording. False if we cannot. */
  async startRecording(): Promise<boolean> {
    if (!this.canRecord || this.recording()) return false;
    this.stopSpeaking();                     // do not record the assistant
    this.micError.set('');
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const rec = new MediaRecorder(stream);
      this.chunks = [];
      rec.addEventListener('dataavailable', e => { if (e.data.size) this.chunks.push(e.data); });
      rec.addEventListener('stop', () => stream.getTracks().forEach(t => t.stop()));
      this.media = rec;
      rec.start();
      this.zone.run(() => this.recording.set(true));
      return true;
    } catch (e) {
      // Almost always a denied permission, or a page served over plain http -
      // getUserMedia needs a secure context, which is why the deployment has TLS.
      this.zone.run(() => this.micError.set(
        'I could not open the microphone. Allow it for this site, or type instead.'));
      return false;
    }
  }

  /**
   * Stop, and return the recording as base64 WAV — or null if nothing was captured.
   *
   * WAV rather than the browser's native webm/opus because Vertex accepts wav
   * and does not document webm. Decoding and re-encoding here costs a moment on
   * a phone and removes a transcoding step from the server, which is the part
   * that would have needed ffmpeg in the image.
   */
  async stopRecording(): Promise<{ base64: string; mimeType: string } | null> {
    const rec = this.media;
    if (!rec) return null;
    const blob: Blob = await new Promise(resolve => {
      rec.addEventListener('stop',
        () => resolve(new Blob(this.chunks, { type: rec.mimeType || 'audio/webm' })),
        { once: true });
      rec.stop();
    });
    this.media = null;
    this.chunks = [];
    this.zone.run(() => this.recording.set(false));
    if (!blob.size) return null;
    try {
      return { base64: await this.toWavBase64(blob), mimeType: 'audio/wav' };
    } catch {
      this.zone.run(() => this.micError.set('That recording could not be read. Please type instead.'));
      return null;
    }
  }

  /** Throw the recording away without transcribing it. */
  cancelRecording(): void {
    const rec = this.media;
    this.media = null;
    this.chunks = [];
    this.recording.set(false);
    try { rec?.stop(); } catch { /* already stopped */ }
  }

  /* Asking for 16 kHz on the AudioContext makes decodeAudioData resample for
   * us, so there is no hand-written resampler to get subtly wrong. 16 kHz mono
   * is what speech recognition wants and it is a quarter the bytes of 44.1. */
  private async toWavBase64(blob: Blob): Promise<string> {
    const ctx = new AudioContext({ sampleRate: 16000 });
    try {
      const decoded = await ctx.decodeAudioData(await blob.arrayBuffer());
      return SpeechService.wavBase64(SpeechService.mono(decoded), decoded.sampleRate);
    } finally {
      void ctx.close();
    }
  }

  /** Average the channels. A phone in a spa is one voice, not a stereo image. */
  private static mono(buf: AudioBuffer): Float32Array {
    if (buf.numberOfChannels === 1) return buf.getChannelData(0);
    const out = new Float32Array(buf.length);
    for (let c = 0; c < buf.numberOfChannels; c++) {
      const ch = buf.getChannelData(c);
      for (let i = 0; i < buf.length; i++) out[i] += ch[i] / buf.numberOfChannels;
    }
    return out;
  }

  private static wavBase64(pcm: Float32Array, rate: number): string {
    const bytes = new ArrayBuffer(44 + pcm.length * 2);
    const view = new DataView(bytes);
    const ascii = (at: number, text: string) => {
      for (let i = 0; i < text.length; i++) view.setUint8(at + i, text.charCodeAt(i));
    };
    ascii(0, 'RIFF');
    view.setUint32(4, 36 + pcm.length * 2, true);
    ascii(8, 'WAVEfmt ');
    view.setUint32(16, 16, true);              // PCM header size
    view.setUint16(20, 1, true);               // format: PCM
    view.setUint16(22, 1, true);               // channels: mono
    view.setUint32(24, rate, true);
    view.setUint32(28, rate * 2, true);        // byte rate
    view.setUint16(32, 2, true);               // block align
    view.setUint16(34, 16, true);              // bits per sample
    ascii(36, 'data');
    view.setUint32(40, pcm.length * 2, true);
    for (let i = 0; i < pcm.length; i++) {
      const v = Math.max(-1, Math.min(1, pcm[i]));
      view.setInt16(44 + i * 2, v < 0 ? v * 0x8000 : v * 0x7FFF, true);
    }
    // Chunked, because String.fromCharCode(...wholeFile) overflows the stack
    // on anything longer than a second or two.
    const b = new Uint8Array(bytes);
    let binary = '';
    for (let i = 0; i < b.length; i += 0x8000) {
      binary += String.fromCharCode(...b.subarray(i, i + 0x8000));
    }
    return btoa(binary);
  }
}

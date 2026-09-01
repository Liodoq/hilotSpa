import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { Toast } from '../../shared/toast/toast';
import { ToastService } from '../../core/toast.service';
import { FormsApi } from '../../core/forms.api';
import { BookingStore } from '../../core/booking.store';
import { AssistantResponse, AssistantSlot, BookingModel, Openings } from '../../core/models';
import { describeHttpError } from '../../core/http-error';
import { SessionLog } from '../../core/booking.store';
import { SpeechService } from '../../core/speech.service';

interface Msg { text: string; mine: boolean; at: string; spoken?: boolean; }

/**
 * C9 — the assistant.
 *
 * The recommendation is REAL as of Sprint 2.16: Spring reads the client's Forms
 * record, filters the catalogue through the spa-authored ServiceProtocol table,
 * asks n8n (Vertex) to rank what is left, and validates the answer again on the
 * way back. Angular never calls n8n directly — n8n cannot check a JWT.
 *
 * The right-hand panel is the honest part, and it is now honest with real
 * numbers: it shows how many services were removed as contraindicated BEFORE
 * the model was asked, and how many of its answers were discarded.
 *
 * Sprint 2.6 — voice and subtitles. Every line the assistant produces goes
 * through say(), so there is exactly one place that speaks and exactly one
 * place that captions. The microphone drops a recognised sentence into the
 * same input box the client could have typed into, which is why voice adds no
 * new path to the server and nothing new to defend.
 */
@Component({
  selector: 'app-book',
  imports: [AppNav, Toast],
  templateUrl: './book.html',
  styleUrl: './book.scss',
})
export class Book implements OnDestroy {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private api = inject(FormsApi);
  private booking = inject(BookingStore);
  protected toast = inject(ToastService);
  protected speech = inject(SpeechService);

  protected loading = signal(true);
  protected error = signal('');
  protected result = signal<AssistantResponse | null>(null);

  messages = signal<Msg[]>([]);
  /** Mirrors the service so the template can style the button; the service
   *  owns the real state. */
  protected listening = computed(() => this.speech.listening());
  draftText = signal('');
  subtitle = signal('Getting your suggestions…');

  /** The assessment this recommendation was built from. */
  protected assessment = computed(() => {
    const id = this.formId();
    return id ? this.booking.find(id) : undefined;
  });

  protected picks = computed(() => this.result()?.recommendations ?? []);

  /** Every time the server said the assistant could name, this turn. */
  protected slots = signal<AssistantSlot[]>([]);

  /**
   * The service the conversation has narrowed to, once the client picks one.
   *
   * This is what makes the panel stop being a menu. Before a choice it offers
   * services; after one it offers that service's times; after a booking it
   * offers neither, because there is nothing left to decide.
   */
  protected focus = signal<string | null>(null);

  /** suggest -> times -> who -> booked. One panel, four jobs, never two at once. */
  protected stage = computed<'suggest' | 'times' | 'who' | 'booked'>(() => {
    if (this.booked()) return 'booked';
    if (this.picked()) return 'who';
    return this.focus() && this.timesForFocus().length ? 'times' : 'suggest';
  });

  /**
   * The time the client tapped, waiting on the optional who-and-where question.
   *
   * Tapping a time no longer books it outright. It used to, and that was the
   * right design while the spa assigned everyone; now that a client may choose
   * their therapist, booking on the first tap would take the choice away at the
   * exact moment they are best placed to make it.
   */
  protected picked = signal<AssistantSlot | null>(null);
  protected openings = signal<Openings | null>(null);
  protected loadingOpenings = signal(false);

  /** null on either means "any available" - the normal case, and the default. */
  protected pickedTherapist = signal<string | null>(null);
  protected pickedRoom = signal<string | null>(null);

  private timesForFocus = computed(() =>
    this.slots().filter(s => s.serviceId === this.focus()));

  /**
   * ONE day at a time — 2026-09-01.
   *
   * This used to print every open time for the whole window at once: four days
   * of half-hour slots is sixty-odd identical chips, all the same weight, and
   * the client had to read the lot to find one. Choosing a time was a search.
   *
   * Now it is two decisions. Which day - and each day says how many times it
   * has before you open it, so a nearly-full day is visible without clicking
   * into it. Then which time, inside morning, afternoon or evening, because
   * nobody thinks "I want 1:30 PM"; they think "sometime after lunch".
   */
  protected pickedDay = signal<string | null>(null);

  /** Every open day for the focused treatment, with how many times it holds. */
  protected days = computed(() => {
    const out: { day: string; count: number }[] = [];
    for (const slot of this.timesForFocus()) {
      const row = out.find(d => d.day === slot.dayLabel);
      if (row) { row.count++; } else { out.push({ day: slot.dayLabel, count: 1 }); }
    }
    return out;
  });

  /**
   * The day on screen: the one they chose, or the soonest that has anything.
   *
   * Falling back rather than showing an empty panel matters when the times
   * refresh under them - a booking they just made can empty the day they were
   * looking at, and a screen that then shows nothing looks broken.
   */
  protected activeDay = computed(() => {
    const chosen = this.pickedDay();
    const all = this.days();
    if (chosen && all.some(d => d.day === chosen)) { return chosen; }
    return all.length ? all[0].day : null;
  });

  /** That day's times, split the way people describe time to each other. */
  protected parts = computed(() => {
    const day = this.activeDay();
    const mine = this.timesForFocus().filter(s => s.dayLabel === day);
    // Read the hour off the ISO start, never off the rendered label - the label
    // is for people and has already been through a formatter.
    const band = (s: AssistantSlot): number => {
      const h = Number((s.start ?? '').slice(11, 13));
      return h < 12 ? 0 : h < 17 ? 1 : 2;
    };
    return ['Morning', 'Afternoon', 'Evening']
      .map((label, i) => ({ label, slots: mine.filter(s => band(s) === i) }))
      .filter(part => part.slots.length > 0);
  });

  chooseDay(day: string): void { this.pickedDay.set(day); }

  protected focusName = computed(() =>
    this.timesForFocus()[0]?.serviceName ?? '');
  protected focusMinutes = computed(() =>
    this.timesForFocus()[0]?.durationMinutes ?? 0);

  protected confirming = signal<string | null>(null);
  protected excludedCount = computed(() => this.result()?.excludedCount ?? 0);
  protected allowedCount = computed(() => this.result()?.allowedCount ?? 0);
  protected rejectedCount = computed(() => this.result()?.rejectedCount ?? 0);

  /** True when the protocol table answered because the model could not. */
  protected degraded = computed(() => this.result()?.status === 'FALLBACK');
  protected referred = computed(() => this.result()?.status === 'REFER');

  private formId = signal<string | null>(null);

  constructor() {
    const id = this.route.snapshot.queryParamMap.get('formId');
    this.formId.set(id);
    void this.load(id);
  }

  /**
   * The client's most recent assessment, when it is recent enough to be reused.
   *
   * Mirrors C2 and, more importantly, mirrors FormsServiceImpl.REUSE_MAX_DAYS —
   * the server is the one that actually enforces this, so a client is never
   * offered something that will come back 409.
   */
  private static readonly REUSE_MAX_DAYS = 60;

  protected previous = computed<SessionLog | null>(() => {
    const last = this.booking.history()[0];
    if (!last?.createdAt) return null;
    const age = (Date.now() - new Date(last.createdAt).getTime()) / 86_400_000;
    return Number.isFinite(age) && age <= Book.REUSE_MAX_DAYS ? last : null;
  });

  /** True while this screen has nothing to work from. */
  protected noAssessment = computed(() => this.formId() === null);
  protected reusing = signal(false);

  private async load(id: string | null): Promise<void> {
    if (!id) {
      // Reached without an assessment — from Home's "book again", from the
      // session report, or by typing the URL. Process Rule #2 still holds: the
      // assistant needs a completed assessment. But refusing and stopping is a
      // dead end, and a client who HAS answered the form before should not be
      // made to answer it again to get past this screen. Offer the copy.
      try { await this.booking.load(); } catch { /* offer the fresh form instead */ }
      this.loading.set(false);
      const prev = this.previous();
      this.say(prev
        ? `I do not have today\u2019s assessment open. I can reuse your answers from `
          + `<b>${prev.date}</b> \u2014 that copies them onto a new record dated today, `
          + `so nothing old is passed off as current.`
        : 'Please complete a short pre-assessment first, and I can suggest what suits you.');
      return;
    }
    try {
      await this.booking.load();
      const res = await this.api.recommend(id);
      this.result.set(res);
      this.openWith(res);
    } catch (e: unknown) {
      console.error('[book] recommend failed', e);
      this.error.set(describeHttpError(e, 'We could not reach the assistant just now.'));
      this.say('I am having trouble right now. The front desk can help you choose.');
    } finally {
      this.loading.set(false);
    }
  }

  private openWith(res: AssistantResponse): void {
    if (res.status === 'REFER') {
      this.say(res.note ?? 'Please speak with the practitioner before booking.');
      return;
    }
    // The spa sells the same treatment at two lengths, so the name on its own
    // reads as a duplicate. Always say the minutes with it.
    const names = res.recommendations
      .map(r => `<b>${r.name}</b> (${r.durationMinutes} min)`).join(', ');
    this.say(`Kumusta po. Based on your assessment, I would suggest ${names}.`);
    for (const r of res.recommendations) {
      this.say(`<b>${r.name}</b>, ${r.durationMinutes} minutes — ${r.reason}`);
    }
    if (res.status === 'FALLBACK') {
      this.say('These come straight from the spa’s own service list today.');
    }
  }

  /**
   * One assistant line: shown, captioned, and spoken.
   *
   * `quiet` exists for the dev diagnostic line only — it belongs on screen but
   * nobody wants a voice reading a stack of debug JSON at a client.
   */
  private say(text: string, quiet = false): void {
    const spoken = !quiet && this.speech.voiceOn() && this.speech.canSpeak;
    this.messages.update(m => [...m, { text, mine: false, at: 'just now', spoken }]);
    this.subtitle.set(`“${text.replace(/<[^>]+>/g, '')}”`);
    this.lastSpoken.set(text);
    if (!quiet) this.speech.speak(text);
  }

  /** The last thing the assistant said, so it can be repeated on demand. A
   *  client who missed a service name should not have to ask again. */
  protected lastSpoken = signal('');

  /**
   * What the caption band shows, in priority order: what is being said now,
   * then what is being heard now, then the last line. The band is never blank
   * and never lies about which of the three is happening.
   */
  protected captionKind = computed<'speaking' | 'listening' | 'idle'>(() => {
    if (this.speech.speaking()) return 'speaking';
    if (this.speech.listening()) return 'listening';
    return 'idle';
  });

  /** The part of the current sentence already spoken — rendered bright. */
  protected captionSaid = computed(() => {
    const c = this.speech.caption();
    return c.text.slice(0, c.spoken);
  });
  /** The part still to come — rendered dim, so the eye can follow the voice. */
  protected captionRest = computed(() => {
    const c = this.speech.caption();
    return c.text.slice(c.spoken);
  });

  /** The line under the band: what the microphone is doing, or why it stopped. */
  protected micNote = computed(() => {
    if (this.speech.micError()) return this.speech.micError();
    if (this.speech.listening()) return this.speech.heard() || 'Listening… speak now.';
    return '';
  });

  protected voiceLabel = computed(() =>
    this.speech.voiceOn() ? 'Voice on — tap to mute' : 'Voice off — tap to hear the assistant');

  toggleVoice(): void {
    const on = this.speech.toggleVoice();
    this.toast.show(on ? 'The assistant will speak' : 'The assistant will stay quiet');
  }

  toggleLang(): void {
    const next = this.speech.toggleLang();
    this.toast.show(next === 'fil-PH' ? 'Filipino / Taglish' : 'English');
  }

  /** Say the last line again from the start. */
  repeat(): void {
    const last = this.lastSpoken();
    if (!last) return;
    this.speech.replay(last);
  }

  /**
   * Copy the earlier assessment onto a record dated today, then carry on here.
   *
   * The server COPIES rather than pointing this visit at an old row: Process
   * Rule #2 asks for a completed assessment per visit, and answers from three
   * months ago are not evidence that anyone was asked anything today. The
   * pain-score-after is never copied.
   */
  async useLast(): Promise<void> {
    const prev = this.previous();
    if (!prev || this.reusing()) return;
    this.reusing.set(true);
    this.speech.stopSpeaking();
    try {
      const saved = await this.api.reuseForm(prev.id);
      if (!saved?.id) throw new Error('no id returned');
      this.formId.set(saved.id);
      // Keep the URL honest, so a refresh lands on the same conversation.
      void this.router.navigate([], {
        relativeTo: this.route, queryParams: { formId: saved.id }, replaceUrl: true,
      });
      this.loading.set(true);
      await this.booking.load();
      const res = await this.api.recommend(saved.id);
      this.result.set(res);
      this.openWith(res);
    } catch (e: unknown) {
      console.error('[book] reuse failed', e);
      const status = (e as { status?: number })?.status;
      this.say(status === 409
        ? 'That assessment is too old to reuse. Please answer the short form again.'
        : 'I could not reuse that just now. Please answer the short form, or ask the front desk.');
    } finally {
      this.reusing.set(false);
      this.loading.set(false);
    }
  }

  /** Straight to C2. The wizard returns here with a formId. */
  startAssessment(): void {
    this.speech.stopSpeaking();
    this.router.navigateByUrl('/assessment/intent');
  }

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }

  /**
   * Whether this browser wants the backend's own diagnostics on screen.
   *
   * OFF unless somebody deliberately turned it on:
   *   localStorage.setItem('hilotspa.dev', '1')
   *
   * It used to be on for everyone, rendered as an ordinary assistant bubble,
   * so a client - or a panellist taking a screenshot - read
   *   (dev: POST http://n8n:5678/... 403 Forbidden)
   * in the same voice and the same weight as the spa talking to them. The
   * information is useful; the placement was not.
   */
  private devMode = (() => {
    try { return localStorage.getItem('hilotspa.dev') === '1'; } catch { return false; }
  })();

  /** Always to the console; to the screen only if this browser asked for it. */
  private noteDebug(debug: string): void {
    console.warn('[assistant]', debug);
    if (this.devMode) {
      this.say(`<span class="devline">dev · ${debug}</span>`, true);
    }
  }

  protected thinking = signal(false);
  /** Set the moment a booking is actually written. */
  protected booked = signal<BookingModel | null>(null);

  async send(): Promise<void> {
    const v = this.draftText().trim();
    const id = this.formId();
    if (!v || this.thinking()) return;

    // The client has taken the turn. Nothing should still be talking over them.
    this.speech.stopSpeaking();
    this.messages.update(m => [...m, { text: v, mine: true, at: 'just now' }]);
    this.draftText.set('');

    if (!id) {
      // Say something USEFUL. Repeating the refusal at a client who has just
      // said they already have an assessment is how a screen becomes a dead
      // end - which is exactly what it did.
      const prev = this.previous();
      this.say(prev
        ? `I can reuse your answers from <b>${prev.date}</b> \u2014 tap \u201cUse it\u201d below `
          + `and I will pick up from there.`
        : 'I need a short pre-assessment before I can suggest anything. '
          + 'Tap the button below \u2014 it takes about a minute.');
      return;
    }

    this.thinking.set(true);
    try {
      const res = await this.api.chat(id, v, this.focus());
      this.slots.set(res.slots ?? []);
      // An empty reply is not a silent assistant - it is the server declining to
      // repeat a sentence that is no longer true (see the CHOOSE path).
      if (res.reply) this.say(res.reply);
      if (res.status === 'CHOOSE' && res.pendingSlotId) {
        const s = (res.slots ?? []).find(x => x.slotId === res.pendingSlotId);
        if (s) {
          this.focus.set(s.serviceId);
          this.pendingOpen = s;
        }
      }
      if (res.status === 'BOOKED' && res.booking) {
        // Confirm from what SPRING returned, never from the model's sentence.
        // If they ever disagree, the database is right.
        const b = res.booking;
        this.booked.set(b);
        this.say(`Booked — <b>${b.serviceName}</b>, ${b.label}, ${b.durationMinutes} min, `
          + `₱${b.price}. ${b.therapist} · ${b.room}. Pay at the counter.`);
        await this.booking.load();
      }
      if (res.debug) {
        this.noteDebug(res.debug);
      }
    } catch (e: unknown) {
      console.error('[book] chat failed', e);
      // The assistant being down must never look like the spa being down.
      this.say('I am having trouble answering right now. '
        + 'The front desk can help you with any question about our services.');
    } finally {
      this.thinking.set(false);
    }

    // Opened AFTER thinking() clears: confirmSlot() refuses to run while a turn
    // is in flight, and that guard is worth keeping rather than working around.
    const pending = this.pendingOpen;
    this.pendingOpen = null;
    if (pending) await this.confirmSlot(pending);
  }

  /**
   * A time the assistant settled in prose, to be opened for the who-and-where
   * question once this turn finishes.
   */
  private pendingOpen: AssistantSlot | null = null;

  /**
   * The microphone.
   *
   * A recognised sentence is put into the draft box and sent through send() —
   * the identical path a typed sentence takes. Voice never reaches Spring in a
   * form the keyboard could not have produced, so there is no second flow to
   * validate and no second flow to defend.
   *
   * Browsers without SpeechRecognition (Safari, most of Android) say so once
   * and leave the client on the typed path rather than on a dead button.
   */
  toggleMic(): void {
    if (this.speech.listening()) { this.speech.stopListening(); return; }

    const started = this.speech.startListening(said => {
      this.draftText.set(said);
      void this.send();
    });

    if (!started) {
      this.toast.show('This browser cannot listen. Chrome or Edge can — or just type.');
    }
  }

  /**
   * Tapping a service is just a faster way of saying it.
   *
   * It puts the sentence into the conversation rather than opening a second,
   * parallel booking UI - so the tap path and the typed path go through exactly
   * the same validation, and there is only one flow to defend.
   */
  choose(serviceId: string): void {
    const picked = this.picks().find(r => r.serviceId === serviceId);
    if (!picked) return;
    this.focus.set(serviceId);
    this.pickedDay.set(null);
    this.draftText.set(
      `I would like the ${picked.name}, ${picked.durationMinutes} minutes. When is it available?`);
    void this.send();
  }

  /** Back to the menu without having to type "actually, something else". */
  clearFocus(): void { this.focus.set(null); this.pickedDay.set(null); }

  /**
   * Dismiss the confirmation and go back to the menu.
   *
   * The booking itself is untouched — it is on the server and on My Bookings.
   * This only decides which panel the conversation is showing.
   */
  bookAnother(): void {
    this.booked.set(null);
    this.focus.set(null);
    this.picked.set(null);
    this.openings.set(null);
  }

  /**
   * Tapping a time books it.
   *
   * This does not go through the model at all. A confirmation that depends on
   * "yes please" being parsed the same way as "I confirm" — and on a long
   * opaque id being copied back without a typo — is a booking staked on two
   * things that will eventually fail. The server revalidates the slotId against
   * freshly computed availability and writes it in the same transaction.
   */
  async confirmSlot(slot: AssistantSlot): Promise<void> {
    const id = this.formId();
    if (!id || this.confirming() || this.thinking()) return;
    this.speech.stopSpeaking();
    this.picked.set(slot);
    this.pickedTherapist.set(null);
    this.pickedRoom.set(null);
    this.openings.set(null);
    this.loadingOpenings.set(true);
    try {
      const open = await this.api.openings(id, slot.slotId);
      this.openings.set(open);
      if (!open.therapists.length || !open.rooms.length) {
        // Both must exist for the visit to happen at all. Saying "pick a
        // therapist" over an empty list would be asking for something we cannot
        // deliver - the honest answer is that the time went.
        this.picked.set(null);
        this.say('That time has just been taken. Here are the times that are still open.');
        await this.refreshTimes(id, slot.serviceId);
      } else {
        this.say(`${slot.dayLabel} ${slot.timeLabel} it is. Would you like a particular `
          + 'therapist or room? You can leave it to us — tap <b>Book this time</b>.');
      }
    } catch (e: unknown) {
      // Say the STATUS out loud. The first version of this swallowed the code
      // and left three very different faults - not deployed, bad request, not
      // permitted - wearing the same apology.
      const status = (e as { status?: number })?.status;
      console.error('[book] openings failed', status, e);
      this.picked.set(null);
      this.say('I could not check who is free just then. Please try another time, '
        + 'or ask the front desk.' + (status ? ` (${status})` : ''));
    } finally {
      this.loadingOpenings.set(false);
    }
  }

  /** Go back to the times without booking. Nothing has been written yet. */
  cancelPick(): void {
    this.picked.set(null);
    this.openings.set(null);
  }

  /** Re-ask the assistant for this treatment's calendar, without a new message. */
  private async refreshTimes(formId: string, serviceId: string): Promise<void> {
    try {
      const res = await this.api.chat(formId, 'What times are still open?', serviceId);
      this.slots.set(res.slots ?? []);
    } catch {
      // The times on screen are now stale but harmless: every one of them is
      // revalidated server-side on the next tap.
    }
  }

  /**
   * Book the tapped time, with whoever and wherever the client chose.
   *
   * Both ids may be null and usually are. A pick that is set is honoured
   * exactly: if that therapist has gone in the meantime the server refuses and
   * says so, rather than quietly handing the client somebody else.
   */
  async commitPick(): Promise<void> {
    const id = this.formId();
    const slot = this.picked();
    if (!id || !slot || this.confirming() || this.thinking()) return;
    this.confirming.set(slot.slotId);
    this.speech.stopSpeaking();

    const who = this.openings()?.therapists.find(x => x.id === this.pickedTherapist());
    const where = this.openings()?.rooms.find(x => x.id === this.pickedRoom());
    const extra = [who ? `with ${who.firstName}` : '', where ? `in ${where.name}` : '']
      .filter(Boolean).join(', ');
    this.messages.update(m => [...m, {
      text: `${slot.serviceName}, ${slot.dayLabel} ${slot.timeLabel}`
        + (extra ? ` ${extra}` : '') + ' — yes please.',
      mine: true, at: 'just now',
    }]);
    try {
      const res = await this.api.confirmSlot(
        id, slot.slotId, this.pickedTherapist(), this.pickedRoom());
      this.slots.set(res.slots ?? []);
      this.say(res.reply);
      if (res.status === 'BOOKED' && res.booking) {
        const b = res.booking;
        this.picked.set(null);
        this.openings.set(null);
        this.booked.set(b);
        this.say(`Booked — <b>${b.serviceName}</b>, ${b.label}, ${b.durationMinutes} min. `
          + `${b.therapist} · ${b.room}. Pay at the counter.`);
        await this.booking.load();
      }
      if (res.debug) {
        console.warn('[assistant]', res.debug);
      }
    } catch (e: unknown) {
      console.error('[book] confirm failed', e);
      // A 409 here is the interesting case and it carries its own reason: the
      // slot went, the CLIENT is already booked at that hour, or the therapist
      // they chose has just been taken. Three different things to do about it,
      // so show what the server actually said rather than one flat apology.
      const reason = (e as { error?: { message?: string } })?.error?.message;
      this.say(reason && reason.trim()
        ? reason
        : 'I could not hold that time just now. Please try another, or ask the front desk.');
      // Re-ask who is free: if it was the therapist that went, the list they are
      // looking at is now a lie.
      const slotNow = this.picked();
      if (slotNow) {
        try {
          const open = await this.api.openings(id, slotNow.slotId);
          this.openings.set(open);
          if (this.pickedTherapist()
              && !open.therapists.some(x => x.id === this.pickedTherapist())) {
            this.pickedTherapist.set(null);
          }
          if (this.pickedRoom() && !open.rooms.some(x => x.id === this.pickedRoom())) {
            this.pickedRoom.set(null);
          }
          if (!open.therapists.length || !open.rooms.length) {
            this.picked.set(null);
            await this.refreshTimes(id, slotNow.serviceId);
          }
        } catch { /* leave the picker as it is; the next tap revalidates */ }
      }
    } finally {
      this.confirming.set(null);
    }
  }

  /** Navigating away by any route — the back button included — must silence it. */
  ngOnDestroy(): void {
    this.speech.stopSpeaking();
    this.speech.stopListening();
  }

  leave(): void {
    this.speech.stopSpeaking();
    this.speech.stopListening();
    this.router.navigateByUrl('/home');
  }
}

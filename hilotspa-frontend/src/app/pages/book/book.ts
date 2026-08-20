import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { Toast } from '../../shared/toast/toast';
import { ToastService } from '../../core/toast.service';
import { SERVICES } from '../../core/demo';
import { AssessmentStore } from '../../core/assessment.store';

interface Msg { text: string; mine: boolean; at: string; spoken?: boolean; }

/**
 * C9 — conversational booking.
 *
 * Voice input/output with subtitles is explicitly in the Scope. The right-hand
 * panel is the honest part: it shows exactly what the assistant was given, and
 * that the contraindicated services were removed BEFORE it was asked. The model
 * cannot name a service that is not in that list.
 *
 * TODO Sprint 2 — replace the canned replies with the n8n webhook, and the mic
 * with the Web Speech API (SpeechRecognition in, SpeechSynthesis out).
 */
@Component({
  selector: 'app-book',
  imports: [AppNav, Toast],
  templateUrl: './book.html',
  styleUrl: './book.scss',
})
export class Book {
  private router = inject(Router);
  protected toast = inject(ToastService);
  private store = inject(AssessmentStore);

  /** Set on C8 before the assessment. The assistant should not ask a question
   *  it already has the answer to. */
  protected wanted = this.store.wantedService();

  protected offer = { month: 'AUG', dayNum: '23', day: 'Sunday', service: 'Hilot sa Likod',
    minutes: 60, time: '2:00 PM – 3:00 PM', therapist: 'Therapist Marites', room: 'Room 2', price: 750 };

  messages = signal<Msg[]>([
    { text: this.wanted
        ? `Kumusta po. Nakita ko sa assessment ninyo — lower back pain, severity 8. ` +
          `Pinili ninyo po ang <b>${this.wanted}</b>. Kailan po kayo pwede?`
        : 'Kumusta po, Ana. Nakita ko sa assessment ninyo — lower back pain, severity 8. ' +
          'Gusto ninyo po ba ng <b>Hilot sa Likod</b>?', mine: false, at: '2:41 PM' },
    { text: 'Opo. Pwede ba sa Sunday hapon?', mine: true, at: '2:41 PM', spoken: true },
    { text: 'May bakante po tayo. Ito ang pinakamalapit sa hiningi ninyo:', mine: false, at: '2:42 PM' },
  ]);
  offerOpen = signal(true);
  listening = signal(false);
  draftText = signal('');
  subtitle = signal('“May bakante po tayo. Ito ang pinakamalapit sa hiningi ninyo.”');

  allowed = computed(() => SERVICES.filter(s => s.suitable).map(s => s.name).join(' · '));
  excludedCount = computed(() => SERVICES.filter(s => !s.suitable).length);

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }

  private push(text: string, mine: boolean, spoken = false): void {
    this.messages.update(m => [...m, { text, mine, spoken, at: 'just now' }]);
  }

  send(): void {
    const v = this.draftText().trim();
    if (!v) return;
    this.push(v, true);
    this.draftText.set('');
    this.reply();
  }

  private reply(): void {
    setTimeout(() => {
      const line = 'Sige po. Tignan ko ulit ang schedule para sa inyo…';
      this.push(line, false);
      this.subtitle.set(`“${line}”`);
    }, 700);
  }

  toggleMic(): void {
    const live = !this.listening();
    this.listening.set(live);
    if (live) { this.subtitle.set('Listening… speak now'); return; }
    this.push('Sa Sunday po sana, mga alas-dos.', true, true);
    this.reply();
  }

  accept(): void {
    this.offerOpen.set(false);
    this.toast.show('Booked. Sending you the details…');
    setTimeout(() => this.router.navigateByUrl('/booking'), 600);
  }

  another(): void {
    this.toast.show('Looking for the next free slot…');
    this.reply();
  }
}

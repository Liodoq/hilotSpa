import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { Toast } from '../../shared/toast/toast';
import { ToastService } from '../../core/toast.service';
import { FormsApi } from '../../core/forms.api';
import { BookingStore } from '../../core/booking.store';
import { AssistantResponse, AssistantSlot, BookingModel } from '../../core/models';
import { describeHttpError } from '../../core/http-error';

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
 * TODO 2.20 — the free-text conversation still uses canned replies. It needs a
 * Spring /assistant/chat relay to the n8n chat workflow.
 * TODO 2.18–2.21 — the offer card needs real availability before it can return.
 */
@Component({
  selector: 'app-book',
  imports: [AppNav, Toast],
  templateUrl: './book.html',
  styleUrl: './book.scss',
})
export class Book {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private api = inject(FormsApi);
  private booking = inject(BookingStore);
  protected toast = inject(ToastService);

  protected loading = signal(true);
  protected error = signal('');
  protected result = signal<AssistantResponse | null>(null);

  messages = signal<Msg[]>([]);
  listening = signal(false);
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

  /** suggest -> times -> booked. One panel, three jobs, never two at once. */
  protected stage = computed<'suggest' | 'times' | 'booked'>(() => {
    if (this.booked()) return 'booked';
    return this.focus() && this.timesForFocus().length ? 'times' : 'suggest';
  });

  private timesForFocus = computed(() =>
    this.slots().filter(s => s.serviceId === this.focus()));

  /** The focused service's times, grouped by day, so a run of half-hours reads
   *  as one row of chips rather than nine sentences. */
  protected days = computed(() => {
    const out: { day: string; slots: AssistantSlot[] }[] = [];
    for (const slot of this.timesForFocus()) {
      const row = out.find(d => d.day === slot.dayLabel);
      if (row) row.slots.push(slot); else out.push({ day: slot.dayLabel, slots: [slot] });
    }
    return out;
  });

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

  private async load(id: string | null): Promise<void> {
    if (!id) {
      // Reached without finishing an assessment. Process Rule #2 — the
      // assistant is not available until there is something to assist with.
      this.loading.set(false);
      this.say('Please complete a pre-assessment first, and I can suggest what suits you.');
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

  private say(text: string): void {
    this.messages.update(m => [...m, { text, mine: false, at: 'just now' }]);
    this.subtitle.set(`“${text.replace(/<[^>]+>/g, '')}”`);
  }

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }

  protected thinking = signal(false);
  /** Set the moment a booking is actually written. */
  protected booked = signal<BookingModel | null>(null);

  async send(): Promise<void> {
    const v = this.draftText().trim();
    const id = this.formId();
    if (!v || this.thinking()) return;

    this.messages.update(m => [...m, { text: v, mine: true, at: 'just now' }]);
    this.draftText.set('');

    if (!id) {
      this.say('Please complete a pre-assessment first, and I can help you choose.');
      return;
    }

    this.thinking.set(true);
    try {
      const res = await this.api.chat(id, v);
      this.slots.set(res.slots ?? []);
      this.say(res.reply);
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
        // dev only: the backend explains its own fallback on screen
        console.warn('[assistant]', res.debug);
        this.say(`<span style="opacity:.7">(dev: ${res.debug})</span>`);
      }
    } catch (e: unknown) {
      console.error('[book] chat failed', e);
      // The assistant being down must never look like the spa being down.
      this.say('I am having trouble answering right now. '
        + 'The front desk can help you with any question about our services.');
    } finally {
      this.thinking.set(false);
    }
  }

  toggleMic(): void {
    this.listening.update(v => !v);
    this.subtitle.set(this.listening() ? 'Listening… speak now' : this.subtitle());
    if (!this.listening()) this.toast.show('Voice input is not wired yet');
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
    this.draftText.set(
      `I would like the ${picked.name}, ${picked.durationMinutes} minutes. When is it available?`);
    void this.send();
  }

  /** Back to the menu without having to type "actually, something else". */
  clearFocus(): void { this.focus.set(null); }

  /**
   * Dismiss the confirmation and go back to the menu.
   *
   * The booking itself is untouched — it is on the server and on My Bookings.
   * This only decides which panel the conversation is showing.
   */
  bookAnother(): void {
    this.booked.set(null);
    this.focus.set(null);
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
    this.confirming.set(slot.slotId);
    this.messages.update(m => [...m, {
      text: `${slot.serviceName}, ${slot.dayLabel} ${slot.timeLabel} — yes please.`,
      mine: true, at: 'just now',
    }]);
    try {
      const res = await this.api.confirmSlot(id, slot.slotId);
      this.slots.set(res.slots ?? []);
      this.say(res.reply);
      if (res.status === 'BOOKED' && res.booking) {
        const b = res.booking;
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
      this.say('I could not hold that time just now. Please try another, or ask the front desk.');
    } finally {
      this.confirming.set(null);
    }
  }

  leave(): void { this.router.navigateByUrl('/home'); }
}

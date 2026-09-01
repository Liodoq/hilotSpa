import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { BodyMap } from '../../shared/body-map/body-map';
import { Toast } from '../../shared/toast/toast';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { AssessmentStore, PainPoint } from '../../core/assessment.store';
import { ProfileStore } from '../../core/profile.store';
import { BookingStore } from '../../core/booking.store';
import { FormsApi } from '../../core/forms.api';

/** C1 — Home / Wellness Profile. Process Rule #1: history belongs to the account. */
@Component({
  selector: 'app-home',
  imports: [AppNav, BodyMap, Toast],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private auth = inject(AuthService);
  private router = inject(Router);
  private store = inject(AssessmentStore);
  protected profileStore = inject(ProfileStore);
  protected toast = inject(ToastService);
  private api = inject(FormsApi);

  protected booking = inject(BookingStore);

  /** The client's OWN latest marks, not a demo figure. Declared AFTER booking:
   *  class fields initialise top-down, so reading this.booking above would be
   *  undefined at construction. */
  protected points = this.booking.lastPoints;

  /** The client's most recent assessment, or undefined for a new account. */
  protected latest = computed(() => this.booking.history()[0]);

  /** Persisted, so a cancellation survives a refresh. */
  next = this.booking.upcoming;

  /**
   * Past SESSIONS - visits that actually happened.
   *
   * This used to be the whole assessment history, so every pre-assessment
   * appeared as a "session" whether or not the client ever came in. That is not
   * a storage problem (an assessment nobody acted on is real data and stays in
   * the database); it is the screen calling an intention a visit.
   */
  past = computed(() => this.booking.history().filter(v => v.appointmentId !== null));

  /**
   * Assessments that never became a visit. Counted, not hidden.
   *
   * Dropping them silently would be the same untruth in the other direction -
   * the client filled those in, and a record that pretends otherwise is a
   * record they cannot trust.
   */
  unbooked = computed(() => this.booking.history().filter(v => v.appointmentId === null).length);

  /** The visit whose rating panel is open, or null. */
  protected rating = signal<string | null>(null);
  /** painPointId -> score, for the panel that is open. */
  protected draftScores = signal<Record<string, number>>({});
  protected saving = signal(false);

  /** A completed visit the client has not scored yet. */
  protected canRate(v: { status: string | null; scores: { after: number | null }[] }): boolean {
    return v.status === 'COMPLETED'
        && v.scores.length > 0
        && v.scores.every(s => s.after === null);
  }

  openRating(v: { id: string; scores: { id: string; before: number | null }[] }): void {
    this.rating.set(v.id);
    // Seed each score with what they came in with. It is the honest starting
    // point - "no change" - and it means a client who only moved one slider has
    // still answered for every point rather than leaving silent nulls that look
    // like missing data later.
    const seed: Record<string, number> = {};
    for (const s of v.scores) { seed[s.id] = s.before ?? 0; }
    this.draftScores.set(seed);
  }

  closeRating(): void {
    this.rating.set(null);
    this.draftScores.set({});
  }

  setScore(pointId: string, score: number): void {
    this.draftScores.update(m => ({ ...m, [pointId]: score }));
  }

  async submitRating(v: { appointmentId: string | null }): Promise<void> {
    if (!v.appointmentId || this.saving()) return;
    this.saving.set(true);
    try {
      const scores = Object.entries(this.draftScores())
        .map(([painPointId, score]) => ({ painPointId, score }));
      await this.api.recordOutcome(v.appointmentId, scores);
      // Re-read rather than patch in place: the server is the record, and a
      // screen that shows what it hoped it wrote is how B69 happened.
      await this.booking.load();
      this.closeRating();
      this.toast.show('Thank you - that helps your therapist next time.');
    } catch {
      this.toast.show('We could not save that just now. Please try again.');
    } finally {
      this.saving.set(false);
    }
  }


  firstName = computed(() => (this.auth.fullName() || 'there').split(' ')[0]);

  startAssessment(): void {
    this.store.reset();
    // profileGuard would catch this anyway; going straight there is friendlier
    // than a redirect the client did not ask for.
    this.router.navigateByUrl(this.profileStore.isComplete()
      ? '/assessment/intent'
      : '/profile?next=/assessment/intent');
  }

  editProfile(): void { this.router.navigateByUrl('/profile'); }

  /**
   * Cancelling needs two taps — 2.32.
   *
   * Not a browser confirm(): a modal dialog blocks the page and reads as an
   * error to someone who is not expecting it. The button becomes the question
   * instead, and the answer sits next to it.
   */
  protected confirmingCancel = signal(false);
  protected cancelling = signal(false);

  askCancel(): void { this.confirmingCancel.set(true); }
  keepVisit(): void { this.confirmingCancel.set(false); }

  async cancelVisit(): Promise<void> {
    const v = this.booking.upcoming();
    if (!v || this.cancelling()) return;
    this.cancelling.set(true);
    try {
      await this.booking.cancel(v.id, 'Cancelled by the client from their home screen');
      this.confirmingCancel.set(false);
      this.toast.show(`Cancelled — ${v.service} on ${v.date}. Nothing to pay.`, 4200);
    } catch (e: unknown) {
      const status = (e as { status?: number })?.status;
      this.toast.show(status === 409
        ? 'That visit has already started. Please speak to the front desk.'
        : 'We could not cancel that just now. Please try again, or call the branch.', 4200);
    } finally {
      this.cancelling.set(false);
    }
  }

  /** TODO Sprint 2 — reschedule reuses the assistant, which already knows how
   *  to negotiate a slot. No second booking UI needed. */
  reschedule(): void { this.router.navigateByUrl('/book'); }

  /** A finished session opens its own read-only record, never the live wizard. */
  viewReport(id: string): void { this.router.navigateByUrl(`/report/${id}`); }
}

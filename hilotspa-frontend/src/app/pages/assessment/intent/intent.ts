import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Shell } from '../../../shared/shell/shell';
import { AssessmentStore } from '../../../core/assessment.store';
import { AuthService } from '../../../core/auth.service';
import { FormsApi } from '../../../core/forms.api';
import { ToastService } from '../../../core/toast.service';
import { AssessmentIntent } from '../../../core/models';
import { BookingStore } from '../../../core/booking.store';

/**
 * C2 — the pain/leisure fork.
 *
 * This screen is the resolution of the contradiction between Process Rule #2
 * (the chatbot is gated behind a completed pre-assessment) and the Significance
 * section (leisure clients book "instantly"). The gate is on assessment
 * COMPLETION, not assessment length: the leisure path is one tap and still
 * writes a completed record with intent = LEISURE.
 */
@Component({
  selector: 'app-intent',
  imports: [Shell],
  templateUrl: './intent.html',
  styleUrl: './intent.scss',
})
export class Intent implements OnInit {
  private router = inject(Router);
  private auth = inject(AuthService);
  protected store = inject(AssessmentStore);
  private api = inject(FormsApi);
  protected toast = inject(ToastService);
  protected bookings = inject(BookingStore);
  protected draft = this.store.draft;
  busy = signal(false);

  /**
   * REUSE is a screen-level choice, not an AssessmentIntent — the enum on the
   * server has two values and the paper is frozen. Keeping it out of the draft
   * means nothing can accidentally post "REUSE" as an intent.
   */
  reuse = signal(false);

  /** How stale an assessment may be and still be offered. Mirrors the server,
   *  which is the one that actually enforces it (FormsServiceImpl). */
  private static readonly MAX_DAYS = 60;

  /** The client's most recent assessment, if it is recent enough to reuse. */
  previous = computed(() => {
    const last = this.bookings.history()[0];
    if (!last?.createdAt) return null;
    const age = (Date.now() - new Date(last.createdAt).getTime()) / 86_400_000;
    return Number.isFinite(age) && age <= Intent.MAX_DAYS ? last : null;
  });

  ngOnInit(): void {
    if (!this.bookings.loaded()) void this.bookings.load();
  }

  choose(intent: AssessmentIntent): void {
    this.reuse.set(false);
    this.store.patch({ intent });
  }

  chooseReuse(): void {
    this.reuse.set(true);
    this.store.patch({ intent: null });
  }

  get chosen(): boolean { return this.reuse() || this.draft().intent !== null; }

  async next(): Promise<void> {
    if (this.busy()) return;

    // REUSE: the server COPIES the earlier assessment into a new record dated
    // today. It does not point this visit at an old row — Process Rule #2 asks
    // for a completed assessment per visit, and a record from three months ago
    // is not evidence that anyone was asked anything today.
    if (this.reuse()) {
      const prev = this.previous();
      if (!prev) { this.reuse.set(false); return; }
      this.busy.set(true);
      try {
        const saved = await this.api.reuseForm(prev.id);
        this.store.reset();
        await this.bookings.load();
        await this.router.navigateByUrl(saved?.id ? `/book?formId=${saved.id}` : '/book');
      } catch (e: unknown) {
        const status = (e as { status?: number })?.status;
        this.toast.show(status === 409
          ? 'That assessment is too old to reuse. Please answer the short form again.'
          : 'We could not reuse that. Check the backend is running, then try again.', 3800);
        this.reuse.set(false);
      } finally {
        this.busy.set(false);
      }
      return;
    }

    // PAIN: on to the body map. Demographics used to sit here as step 2; they
    // are on the profile now, so a returning client goes straight to it.
    if (!this.store.isLeisure()) {
      this.router.navigateByUrl('/assessment/body-map');
      return;
    }

    // LEISURE: one tap. No review screen, straight to the assistant — but the
    // record IS written first.
    //
    // This is the whole of §E1. Process Rule #2 gates the chatbot behind a
    // COMPLETED pre-assessment. If "relax" reached the assistant with nothing
    // saved, the fork would be cosmetic and the defense line would collapse.
    // The gate is on completion, not on length: a leisure client completes it
    // in one tap, and intent = LEISURE makes that record distinguishable.
    this.busy.set(true);
    try {
      let branchId = this.auth.branchId();
      if (!branchId) branchId = (await this.api.branches())[0]?.id ?? null;
      if (!branchId) throw new Error('no-branch');
      const saved = await this.api.createForm(this.store.toFormsModel(branchId));
      this.store.reset();
      await this.router.navigateByUrl(saved?.id ? `/book?formId=${saved.id}` : '/book');
    } catch {
      this.toast.show('We could not save that. Check the backend is running, then try again.', 3600);
    } finally {
      this.busy.set(false);
    }
  }

  back(): void{
    this.router.navigateByUrl('/home');
  }
  
  // leave(): void {
  //   this.store.reset();
  //   this.auth.logout();
  // }
}

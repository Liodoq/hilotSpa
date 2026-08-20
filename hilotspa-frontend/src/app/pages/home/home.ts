import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { BodyMap } from '../../shared/body-map/body-map';
import { Toast } from '../../shared/toast/toast';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { AssessmentStore, PainPoint } from '../../core/assessment.store';
import { ProfileStore } from '../../core/profile.store';
import { LAST_POINTS } from '../../core/demo';
import { BookingStore } from '../../core/booking.store';

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

  protected points: PainPoint[] = LAST_POINTS;
  protected booking = inject(BookingStore);

  /** Persisted, so a cancellation survives a refresh. */
  next = this.booking.upcoming;
  past = this.booking.history;


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

  /** TODO Sprint 2 — DELETE /api/v1/appointments/{id}. */
  cancelVisit(): void {
    const v = this.booking.cancel();
    if (v) this.toast.show(`Cancelled — ${v.day} ${v.dayNum} ${v.month} is free again`, 3000);
  }

  /** TODO Sprint 2 — reschedule reuses the assistant, which already knows how
   *  to negotiate a slot. No second booking UI needed. */
  reschedule(): void { this.router.navigateByUrl('/book'); }

  /** A finished session opens its own read-only record, never the live wizard. */
  viewReport(id: string): void { this.router.navigateByUrl(`/report/${id}`); }
}

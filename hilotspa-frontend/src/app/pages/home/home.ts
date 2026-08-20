import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { BodyMap } from '../../shared/body-map/body-map';
import { Toast } from '../../shared/toast/toast';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { AssessmentStore, PainPoint } from '../../core/assessment.store';
import { ProfileStore } from '../../core/profile.store';
import { LAST_POINTS, NEXT_VISIT, PAST_VISITS } from '../../core/demo';

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

  protected next = NEXT_VISIT;
  protected past = PAST_VISITS;
  protected points: PainPoint[] = LAST_POINTS;

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
}

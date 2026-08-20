import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Shell } from '../../../shared/shell/shell';
import { AssessmentStore } from '../../../core/assessment.store';
import { AuthService } from '../../../core/auth.service';
import { AssessmentIntent } from '../../../core/models';

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
export class Intent {
  private router = inject(Router);
  private auth = inject(AuthService);
  protected store = inject(AssessmentStore);
  protected draft = this.store.draft;

  choose(intent: AssessmentIntent): void {
    this.store.patch({ intent });
  }

  next(): void {
    // Demographics used to sit here as step 2. They are on the profile now, so
    // a returning client goes straight to the map.
    // The leisure path skips the map, the checklist and the history questions —
    // but still lands on review, and still saves a completed record.
    this.router.navigateByUrl(
      this.store.isLeisure() ? '/assessment/review' : '/assessment/body-map');
  }

  leave(): void {
    this.store.reset();
    this.auth.logout();
  }
}

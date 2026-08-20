import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Shell } from '../../../shared/shell/shell';
import { AssessmentStore } from '../../../core/assessment.store';
import { AuthService } from '../../../core/auth.service';
import { FormsApi } from '../../../core/forms.api';
import { ToastService } from '../../../core/toast.service';
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
  private api = inject(FormsApi);
  protected toast = inject(ToastService);
  protected draft = this.store.draft;
  busy = signal(false);

  choose(intent: AssessmentIntent): void {
    this.store.patch({ intent });
  }

  async next(): Promise<void> {
    if (this.busy()) return;

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
      await this.api.createForm(this.store.toFormsModel(branchId));
      this.store.reset();
      await this.router.navigateByUrl('/book');
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

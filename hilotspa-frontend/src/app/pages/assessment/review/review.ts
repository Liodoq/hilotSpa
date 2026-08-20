import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Shell } from '../../../shared/shell/shell';
import { BodyMap } from '../../../shared/body-map/body-map';
import { AssessmentStore } from '../../../core/assessment.store';
import { ProfileStore } from '../../../core/profile.store';
import { AuthService } from '../../../core/auth.service';
import { FormsApi } from '../../../core/forms.api';
import { AnatomicalRegion, COMPLAINTS, REGIONS } from '../../../core/models';
import { describeHttpError } from '../../../core/http-error';

/**
 * C7 — the ONLY write. Everything from C2 to C6 lives in AssessmentStore and is
 * posted here in a single request, because Forms is an aggregate root on the
 * server and a pain point cannot exist without its parent assessment.
 */
@Component({
  selector: 'app-review',
  imports: [Shell, BodyMap],
  templateUrl: './review.html',
  styleUrl: './review.scss',
})
export class Review {
  private router = inject(Router);
  private auth = inject(AuthService);
  private api = inject(FormsApi);
  protected store = inject(AssessmentStore);
  protected profileStore = inject(ProfileStore);
  protected draft = this.store.draft;
  protected profile = this.profileStore.profile;

  busy = signal(false);
  error = signal('');

  mainLabel = computed(() =>
    COMPLAINTS.find(c => c.value === this.draft().mainComplaint)?.label ?? '');

  others = computed(() =>
    COMPLAINTS.filter(c =>
      this.draft().complaints.includes(c.value) && c.value !== this.draft().mainComplaint)
      .map(c => c.label));

  label(r: AnatomicalRegion): string { return REGIONS.find(x => x.value === r)?.label ?? r; }

  go(step: string): void { this.router.navigateByUrl(`/assessment/${step}`); }

  /** Demographics live on the profile, so "Edit" there leaves the wizard. */
  editProfile(): void { this.router.navigateByUrl('/profile?next=/assessment/review'); }

  back(): void {
    this.router.navigateByUrl(
      this.store.isLeisure() ? '/assessment/intent' : '/assessment/history');
  }

  async submit(): Promise<void> {
    if (this.busy() || !this.draft().consented) return;
    this.busy.set(true);
    this.error.set('');
    try {
      // branchId comes from the token for staff; a customer has none, so we
      // fall back to the first branch. When multi-branch selection ships this
      // becomes a picker on C2 — see the note in paper-deltas §B1.
      let branchId = this.auth.branchId();
      if (!branchId) {
        const branches = await this.api.branches();
        branchId = branches[0]?.id ?? null;
      }
      if (!branchId) throw new Error('no-branch');

      await this.api.createForm(this.store.toFormsModel(branchId));
      this.store.reset();
      await this.router.navigateByUrl('/assessment/intent');
    } catch (e: unknown) {
      console.error('[review] submit failed', e);
      this.error.set(describeHttpError(e, 'We could not save your assessment.'));
    } finally {
      this.busy.set(false);
    }
  }

  leave(): void { this.store.reset(); this.auth.logout(); }
}

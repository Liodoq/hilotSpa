import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Shell } from '../../../shared/shell/shell';
import { AssessmentStore } from '../../../core/assessment.store';
import { ProfileStore } from '../../../core/profile.store';
import { PRESSURES, SAFETY_FLAGS, THERAPIST_PREFERENCES } from '../../../core/models';

/**
 * C6 — both history questions are quoted verbatim from Appendix A.
 *
 * The safety checklist is NOT the system making a clinical decision. It is the
 * input to a contraindication table that the spa's own practitioner authored
 * and signed; the filtering happens in Java, before the AI is ever called.
 */
@Component({
  selector: 'app-history',
  imports: [Shell],
  templateUrl: './history.html',
  styleUrl: './history.scss',
})
export class History {
  private router = inject(Router);
  protected store = inject(AssessmentStore);
  private profile = inject(ProfileStore);
  protected draft = this.store.draft;

  protected therapyKinds = ['Hilot', 'Massage', 'Physical therapy', 'Chiropractic', 'Acupuncture', 'Other'];
  protected pressures = PRESSURES;
  protected therapistOptions = THERAPIST_PREFERENCES;
  /**
   * Pregnancy is not offered when the profile records male.
   *
   * It is not only noise: a flag nobody can truthfully tick still reaches the
   * assistant's prompt as a safety consideration, and the paper form does not
   * ask men either.
   */
  protected flags = computed(() => {
    // The list, its wording and its order all live in models.ts now, in step
    // with SafetyFlag.java. What is stored is the enum name, so the sentence can
    // be reworded later without orphaning every record that used the old one.
    const sex = (this.profile.profile().sex ?? '').toLowerCase();
    return sex.startsWith('m')
      ? SAFETY_FLAGS.filter(f => f.value !== 'PREGNANT')
      : SAFETY_FLAGS;
  });

  val(ev: Event): string {
    return (ev.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value;
  }

  back(): void { this.router.navigateByUrl('/assessment/complaints'); }
  next(): void { this.router.navigateByUrl('/assessment/review'); }
  leave(): void { this.router.navigateByUrl('/home'); }
}

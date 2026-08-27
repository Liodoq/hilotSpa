import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Shell } from '../../../shared/shell/shell';
import { AssessmentStore } from '../../../core/assessment.store';
import { ProfileStore } from '../../../core/profile.store';

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
  protected pressures = ['Light', 'Medium', 'Firm'];
  /**
   * Pregnancy is not offered when the profile records male.
   *
   * It is not only noise: a flag nobody can truthfully tick still reaches the
   * assistant's prompt as a safety consideration, and the paper form does not
   * ask men either.
   */
  protected flags = computed(() => {
    const all = [
      'Pregnant',
      'High blood pressure',
      'Heart condition',
      'Diabetes',
      'Varicose veins',
      'Fracture or surgery in the last 6 weeks',
      'Open wound or skin infection',
      'Cancer, or under treatment',
      'Taking blood thinners',
      'Osteoporosis',
    ];
    const sex = (this.profile.profile().sex ?? '').toLowerCase();
    return sex.startsWith('m') ? all.filter(f => f !== 'Pregnant') : all;
  });

  val(ev: Event): string {
    return (ev.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value;
  }

  back(): void { this.router.navigateByUrl('/assessment/complaints'); }
  next(): void { this.router.navigateByUrl('/assessment/review'); }
  leave(): void { this.router.navigateByUrl('/home'); }
}

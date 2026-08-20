import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Shell } from '../../../shared/shell/shell';
import { AssessmentStore } from '../../../core/assessment.store';
import { AuthService } from '../../../core/auth.service';
import { COMPLAINTS } from '../../../core/models';

/**
 * C5 — the 24 conditions from Appendix A, plus Others.
 *
 * The labels are the exact wording on the physical form. They are not
 * paraphrased and not reordered: a client who has filled in the paper version
 * should recognise this screen line for line.
 */
@Component({
  selector: 'app-complaints',
  imports: [Shell],
  templateUrl: './complaints.html',
  styleUrl: './complaints.scss',
})
export class Complaints {
  private router = inject(Router);
  private auth = inject(AuthService);
  protected store = inject(AssessmentStore);
  protected draft = this.store.draft;

  protected durations = ['Less than a week', '1 – 4 weeks', '1 – 6 months', 'More than 6 months'];

  filter = signal('');

  /** 24 items is a lot to scan, so the list filters. OTHER is handled
   *  separately below the grid because it owns a text field. */
  shown = computed(() => {
    const q = this.filter().trim().toLowerCase();
    const all = COMPLAINTS.filter(c => c.value !== 'OTHER');
    return q ? all.filter(c => c.label.toLowerCase().includes(q)) : all;
  });

  /** Only a condition you actually ticked can be the chief complaint. */
  ticked = computed(() =>
    COMPLAINTS.filter(c => this.draft().complaints.includes(c.value)));

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }

  back(): void { this.router.navigateByUrl('/assessment/body-map'); }
  next(): void { this.router.navigateByUrl('/assessment/history'); }
  leave(): void { this.store.reset(); this.auth.logout(); }
}

import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Shell } from '../../../shared/shell/shell';
import { AssessmentStore } from '../../../core/assessment.store';
import { AuthService } from '../../../core/auth.service';

/** C3 — mirrors the identification block of the paper intake form. */
@Component({
  selector: 'app-demographics',
  imports: [Shell],
  templateUrl: './demographics.html',
  styleUrl: './demographics.scss',
})
export class Demographics {
  private router = inject(Router);
  private auth = inject(AuthService);
  protected store = inject(AssessmentStore);
  protected draft = this.store.draft;

  protected sexes = ['Female', 'Male'];
  protected statuses = ['Single', 'Married', 'Widowed', 'Separated'];

  /** Age is derived, never typed. Two fields that can disagree will disagree. */
  age = computed(() => {
    const b = this.draft().birthDate;
    if (!b) return null;
    const d = new Date(b);
    if (Number.isNaN(d.getTime())) return null;
    const now = new Date();
    let a = now.getFullYear() - d.getFullYear();
    const m = now.getMonth() - d.getMonth();
    if (m < 0 || (m === 0 && now.getDate() < d.getDate())) a--;
    return a >= 0 && a < 130 ? a : null;
  });

  val(ev: Event): string { return (ev.target as HTMLInputElement | HTMLSelectElement).value; }

  num(ev: Event): number | null {
    const raw = (ev.target as HTMLInputElement).value.trim();
    if (raw === '') return null;
    const n = Number(raw);
    return Number.isFinite(n) ? n : null;
  }

  setBirth(v: string): void { this.store.patch({ birthDate: v || null }); }

  back(): void { this.router.navigateByUrl('/assessment/intent'); }

  next(): void {
    // The leisure path skips the pain map, the checklist and the history
    // questions. It still ends on review, and it still saves a complete record.
    this.router.navigateByUrl(
      this.store.isLeisure() ? '/assessment/review' : '/assessment/body-map');
  }

  leave(): void { this.store.reset(); this.auth.logout(); }
}

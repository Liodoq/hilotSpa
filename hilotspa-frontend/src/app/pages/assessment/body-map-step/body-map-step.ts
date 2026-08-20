import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Shell } from '../../../shared/shell/shell';
import { BodyMap } from '../../../shared/body-map/body-map';
import { AssessmentStore, PainPoint } from '../../../core/assessment.store';
import { AuthService } from '../../../core/auth.service';
import { AnatomicalRegion, BodyView, REGIONS } from '../../../core/models';
import { guessRegion, severityClass } from '../../../core/region.util';

/** C4 — the headline feature. Front and back are shown together rather than
 *  behind a toggle: a hidden view is a view nobody fills in. */
@Component({
  selector: 'app-body-map-step',
  imports: [Shell, BodyMap],
  templateUrl: './body-map-step.html',
  styleUrl: './body-map-step.scss',
})
export class BodyMapStep {
  private router = inject(Router);
  private auth = inject(AuthService);
  protected store = inject(AssessmentStore);
  protected draft = this.store.draft;

  protected regions = REGIONS;
  protected scores = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
  protected qualities = ['Pain', 'Stiff', 'Weak', 'Numb'];   // from the paper form
  protected sev = severityClass;

  selectedKey = signal<string | null>(null);
  selected = computed<PainPoint | undefined>(() =>
    this.draft().points.find(p => p.key === this.selectedKey()));

  place(view: BodyView, at: { x: number; y: number }): void {
    const key = this.store.addPoint({
      view, x: at.x, y: at.y,
      region: guessRegion(view, at.x, at.y),
      score: 5,
      qualities: ['Pain'],
    });
    this.selectedKey.set(key);
  }

  update(key: string, part: Partial<PainPoint>): void { this.store.updatePoint(key, part); }

  toggleQuality(key: string, q: string): void {
    const p = this.draft().points.find(x => x.key === key);
    if (!p) return;
    const qualities = p.qualities.includes(q)
      ? p.qualities.filter(x => x !== q)
      : [...p.qualities, q];
    this.store.updatePoint(key, { qualities });
  }

  remove(key: string): void {
    this.store.removePoint(key);
    if (this.selectedKey() === key) this.selectedKey.set(null);
  }

  indexOf(key: string): number { return this.draft().points.findIndex(p => p.key === key) + 1; }

  label(r: AnatomicalRegion): string { return REGIONS.find(x => x.value === r)?.label ?? r; }

  anyVal(ev: Event): AnatomicalRegion {
    return (ev.target as HTMLSelectElement).value as AnatomicalRegion;
  }

  back(): void { this.router.navigateByUrl('/assessment/demographics'); }
  next(): void { this.router.navigateByUrl('/assessment/complaints'); }
  leave(): void { this.store.reset(); this.auth.logout(); }
}

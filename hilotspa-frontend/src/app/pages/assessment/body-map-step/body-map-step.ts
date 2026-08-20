import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Shell } from '../../../shared/shell/shell';
import { BodyMap } from '../../../shared/body-map/body-map';
import { AssessmentStore, PainPoint } from '../../../core/assessment.store';
import { ToastService } from '../../../core/toast.service';
import { AnatomicalRegion, REGIONS } from '../../../core/models';
import { severityClass } from '../../../core/region.util';
import { Hotspot, sideLabel } from '../../../core/body-hotspots';

/**
 * C4 — the headline feature.
 *
 * Front and back are shown together rather than behind a toggle: a hidden view
 * is a view nobody fills in. Marks snap to fixed anatomical positions, so the
 * region is never guessed and a marker can never land in empty space.
 */
@Component({
  selector: 'app-body-map-step',
  imports: [Shell, BodyMap],
  templateUrl: './body-map-step.html',
  styleUrl: './body-map-step.scss',
})
export class BodyMapStep {
  private router = inject(Router);
  private toast = inject(ToastService);
  protected store = inject(AssessmentStore);
  protected draft = this.store.draft;

  protected scores = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
  protected qualities = ['Pain', 'Stiff', 'Weak', 'Numb'];   // from the paper form
  protected sev = severityClass;

  selectedKey = signal<string | null>(null);
  selected = computed<PainPoint | undefined>(() =>
    this.draft().points.find(p => p.key === this.selectedKey()));

  /** While a spot is open it must be finished. Otherwise people tap five times
   *  and leave five spots at the default severity, which is what happened. */
  editing = computed(() => this.selected() !== undefined);

  place(h: Hotspot): void {
    const key = this.store.addPoint({
      hotspotId: h.id, view: h.view, x: h.x, y: h.y,
      region: h.region, side: h.side, score: 5, qualities: ['Pain'],
    });
    this.selectedKey.set(key);
  }

  blocked(): void {
    this.toast.show('Finish the spot you are on first — press Done or Remove.', 2600);
  }

  update(key: string, part: Partial<PainPoint>): void { this.store.updatePoint(key, part); }

  toggleQuality(key: string, q: string): void {
    const p = this.draft().points.find(x => x.key === key);
    if (!p) return;
    const qualities = p.qualities.includes(q)
      ? p.qualities.filter(x => x !== q)
      : [...p.qualities, q];
    // At least one quality must stay ticked — a spot that is neither painful,
    // stiff, weak nor numb is not a finding.
    if (qualities.length === 0) return;
    this.store.updatePoint(key, { qualities });
  }

  remove(key: string): void {
    this.store.removePoint(key);
    if (this.selectedKey() === key) this.selectedKey.set(null);
  }

  indexOf(key: string): number { return this.draft().points.findIndex(p => p.key === key) + 1; }

  label(r: AnatomicalRegion): string { return REGIONS.find(x => x.value === r)?.label ?? r; }

  fullLabel(p: PainPoint): string {
    const side = sideLabel(p.side);
    return side ? `${this.label(p.region)} · ${side}` : this.label(p.region);
  }

  back(): void { this.router.navigateByUrl('/assessment/intent'); }

  next(): void {
    if (this.editing()) { this.blocked(); return; }
    this.router.navigateByUrl('/assessment/complaints');
  }

  leave(): void { this.router.navigateByUrl('/home'); }
}

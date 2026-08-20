import { Component, computed, input, output } from '@angular/core';
import { BodyView } from '../../core/models';
import { PainPoint } from '../../core/assessment.store';
import { severityClass } from '../../core/region.util';

/**
 * The digital replacement for "CIRCLE the area of PAIN" on the paper form.
 * Coordinates are emitted as percentages so a marker means the same thing on a
 * phone, a counter tablet, and the defense projector.
 */
@Component({
  selector: 'app-body-map',
  templateUrl: './body-map.html',
  styleUrl: './body-map.scss',
})
export class BodyMap {
  view = input.required<BodyView>();
  points = input<PainPoint[]>([]);
  selectedKey = input<string | null>(null);

  place = output<{ x: number; y: number }>();
  select = output<string>();

  /** Only the markers belonging to this view. bodyView exists because an x/y
   *  pair is ambiguous without it — see paper-deltas §B3. */
  visible = computed(() => this.points().filter(p => p.view === this.view()));

  sev = severityClass;

  /** 1-based label, numbered across BOTH views so the list on the right and the
   *  figures agree. */
  indexOf(key: string): number {
    return this.points().findIndex(p => p.key === key) + 1;
  }

  haloSize(score: number): number {
    return 46 + score * 5;
  }

  onPlace(ev: MouseEvent): void {
    const host = ev.currentTarget as HTMLElement;
    const r = host.getBoundingClientRect();
    const x = ((ev.clientX - r.left) / r.width) * 100;
    const y = ((ev.clientY - r.top) / r.height) * 100;
    this.place.emit({ x: clamp(x), y: clamp(y) });
  }

  onSelect(ev: MouseEvent, key: string): void {
    ev.stopPropagation();   // otherwise selecting a marker also places a new one
    this.select.emit(key);
  }
}

function clamp(n: number): number {
  return Math.max(0, Math.min(100, Math.round(n * 10) / 10));
}

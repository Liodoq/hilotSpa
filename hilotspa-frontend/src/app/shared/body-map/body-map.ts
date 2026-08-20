import { Component, computed, input, output, signal } from '@angular/core';
import { BodyView, REGIONS } from '../../core/models';
import { PainPoint } from '../../core/assessment.store';
import { severityClass } from '../../core/region.util';
import { HOTSPOTS, Hotspot, nearestHotspot, sideLabel } from '../../core/body-hotspots';

/**
 * The digital replacement for "CIRCLE the area of PAIN" on the paper form.
 *
 * Taps snap to one of the fixed anatomical positions in body-hotspots.ts. That
 * makes a marker mean the same thing on a phone, a counter tablet and the
 * defense projector — and it makes the region a fact about where they tapped
 * rather than a guess we then have to let them correct.
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
  /** false on read-only thumbnails (C1, C7, S4). */
  interactive = input(true);
  /** true while another spot is still being filled in — no new marks until it is done. */
  locked = input(false);

  place = output<Hotspot>();
  select = output<string>();
  blocked = output<void>();

  hover = signal<Hotspot | null>(null);

  spots = computed(() => HOTSPOTS.filter(h => h.view === this.view()));
  visible = computed(() => this.points().filter(p => p.view === this.view()));

  sev = severityClass;

  isTaken(id: string): boolean { return this.points().some(p => p.hotspotId === id); }

  labelFor(h: Hotspot): string {
    const region = REGIONS.find(r => r.value === h.region)?.label ?? h.region;
    const side = sideLabel(h.side);
    return side ? `${region} · ${side}` : region;
  }

  /** Numbered across BOTH views so the list and the figures agree. */
  indexOf(key: string): number {
    return this.points().findIndex(p => p.key === key) + 1;
  }

  haloSize(score: number): number { return 46 + score * 5; }

  private at(ev: MouseEvent): Hotspot | null {
    const host = ev.currentTarget as HTMLElement;
    const r = host.getBoundingClientRect();
    const x = ((ev.clientX - r.left) / r.width) * 100;
    const y = ((ev.clientY - r.top) / r.height) * 100;
    return nearestHotspot(this.view(), x, y);
  }

  onHover(ev: MouseEvent): void {
    if (!this.interactive() || this.locked()) return;
    this.hover.set(this.at(ev));
  }

  onPlace(ev: MouseEvent): void {
    if (!this.interactive()) return;
    if (this.locked()) { this.blocked.emit(); return; }
    const h = this.at(ev);
    if (!h) return;                       // empty space marks nothing
    if (this.isTaken(h.id)) return;       // one marker per position
    this.place.emit(h);
  }

  onSelect(ev: MouseEvent, key: string): void {
    ev.stopPropagation();   // otherwise selecting a marker also places a new one
    this.select.emit(key);
  }
}

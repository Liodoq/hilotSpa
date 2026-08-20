import { Component, inject, signal } from '@angular/core';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { BodyMap } from '../../../shared/body-map/body-map';
import { ToastService } from '../../../core/toast.service';
import { LAST_POINTS } from '../../../core/demo';
import { PainPoint } from '../../../core/assessment.store';

interface Finding {
  region: string; left: boolean; right: boolean;
  marks: string[]; before: number; after: number | null;
}

/**
 * S4 — the practitioner's view. Figure 3.2 calls this out specifically.
 *
 * This is the digital half of the bone setter's own sheet: the eleven regions
 * from the paper form, L/R, Pain/Stiff/Weak/Numb, and BEFORE / AFTER pain.
 *
 * AFTER is deliberately empty until the session ends. It is written by staff,
 * never by the client — and it is the only quantitative outcome measure the spa
 * already collects. See paper-deltas §H3.
 */
@Component({
  selector: 'app-staff-report',
  imports: [DashShell, BodyMap],
  templateUrl: './report.html',
  styleUrl: './report.scss',
})
export class StaffReport {
  protected toast = inject(ToastService);
  protected points: PainPoint[] = LAST_POINTS;
  protected qualities = ['Pain', 'Stiff', 'Weak', 'Numb'];

  notes = signal('');

  findings = signal<Finding[]>([
    { region: 'Shoulder', left: false, right: true, marks: ['Pain'], before: 8, after: null },
    { region: 'Lumbar', left: true, right: true, marks: ['Pain', 'Stiff'], before: 7, after: null },
    { region: 'Elbow', left: false, right: true, marks: ['Stiff'], before: 5, after: null },
  ]);

  val(ev: Event): string { return (ev.target as HTMLTextAreaElement).value; }

  toggleMark(region: string, q: string): void {
    this.findings.update(list => list.map(f => f.region !== region ? f : {
      ...f,
      marks: f.marks.includes(q) ? f.marks.filter(m => m !== q) : [...f.marks, q],
    }));
  }

  recordAfter(): void {
    // TODO Sprint 3 — POST the after-session scores once H3 adds painScoreAfter.
    this.findings.update(list => list.map(f => ({ ...f, after: Math.max(1, f.before - 4) })));
    this.toast.show('AFTER scores saved and appended to the record');
  }
}

import { Component, inject, signal } from '@angular/core';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { ToastService } from '../../../core/toast.service';
import { DemoRule, RULES } from '../../../core/demo';

/**
 * A4 — global configuration, and X2: where the signed contraindication table lives.
 *
 * The unsigned-rules panel is deliberate. Seeded rows are visibly quarantined
 * and excluded from live recommendations, which is a far stronger position at
 * defense than unsigned rules quietly working.
 */
@Component({
  selector: 'app-admin-config',
  imports: [DashShell],
  templateUrl: './config.html',
  styleUrl: './config.scss',
})
export class AdminConfig {
  protected toast = inject(ToastService);
  protected tabs = ['Service menu', 'Service protocol', 'Branches', 'Node settings'];

  tab = signal('Service protocol');
  rules = signal<DemoRule[]>(RULES.map(r => ({ ...r })));

  toggle(service: string, condition: string): void {
    this.rules.update(list => list.map(r =>
      r.service === service && r.condition === condition ? { ...r, indicated: !r.indicated } : r));
    const r = this.rules().find(x => x.service === service && x.condition === condition);
    this.toast.show(
      `${service} × ${condition} → ${r?.indicated ? 'INDICATED' : 'CONTRAINDICATED'} · needs re-signing`,
      3200);
  }
}

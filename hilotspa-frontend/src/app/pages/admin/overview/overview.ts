import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { AdminApi, Health, Overview } from '../../../core/admin.api';
import { BranchContext } from '../../../core/branch-context';

/**
 * A1 — node aggregation. Figure 3.3.
 *
 * Every figure is counted from the database when the page loads, so the same
 * number can be reproduced in psql (backend/verify.sql). Two things are
 * deliberately absent: takings, because every service still seeds at ₱0.00
 * until the spa hands over its rate card; and a second node drawn as "offline",
 * because the node registry is Sprint 3 and does not exist yet. Showing a
 * picture of a feature is not the feature.
 */
@Component({
  selector: 'app-admin-overview',
  imports: [DashShell],
  templateUrl: './overview.html',
  styleUrl: './overview.scss',
})
export class AdminOverview implements OnInit {
  private router = inject(Router);
  private api = inject(AdminApi);
  private ctx = inject(BranchContext);

  data = signal<Overview | null>(null);

  /**
   * Operational readiness. Loaded separately and never allowed to break the
   * page: a health check that takes the screen down with it is worse than none.
   */
  health = signal<Health | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  nodes = computed(() => this.data()?.nodes ?? []);
  readiness = computed(() => this.data()?.readiness ?? null);

  /** True when something on this screen would mislead a visitor if unstated. */
  blocked = computed(() => {
    const r = this.readiness();
    return !!r && (r.unsignedRules > 0 || r.servicesWithoutPrice > 0);
  });
  complaints = computed(() => this.data()?.topComplaints ?? []);
  assistant = computed(() => this.data()?.assistant ?? null);

  heading = computed(() => {
    const at = this.data()?.generatedAt;
    const d = at ? new Date(at) : new Date();
    return d.toLocaleString('en-GB',
      { weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' });
  });

  async ngOnInit(): Promise<void> { await this.load(); }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [overview, health] = await Promise.all([
        this.api.overview(),
        this.api.health().catch(() => null),
      ]);
      this.data.set(overview);
      this.health.set(health);
    } catch {
      this.error.set('We could not build the aggregate.');
    } finally {
      this.loading.set(false);
    }
  }

  /** Everything the health check is unhappy about, worst first. */
  faults = computed(() =>
    (this.health()?.checks ?? []).filter(c => c.state !== 'OK')
      .sort((a, b) => (a.state === 'DOWN' ? -1 : 1) - (b.state === 'DOWN' ? -1 : 1)));

  when(iso: string | null): string {
    if (!iso) return 'no writes yet';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '—'
      : d.toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
  }

  /** Opening a branch from here switches context, then lands on A2. */
  open(n: { branchId: string; branchName: string }): void {
    this.ctx.enter({ id: n.branchId, name: n.branchName });
    this.router.navigateByUrl('/admin/branches');
  }

  /** Deep links, so a warning can hand you the screen that fixes it. */
  goConfig(tab: string): void {
    this.router.navigate(['/admin/config'], { queryParams: { tab } });
  }

  goAudit(action: string): void {
    this.router.navigate(['/admin/audit'], { queryParams: { action } });
  }
}

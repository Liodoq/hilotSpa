import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { AdminApi, Health, NodeView, Overview } from '../../../core/admin.api';
import { BranchContext } from '../../../core/branch-context';

/**
 * A1 — node aggregation. Figure 3.3.
 *
 * Every figure is counted from the database when the page loads, so the same
 * number can be reproduced in psql (backend/verify.sql). Takings are
 * deliberately absent: every service still seeds at ₱0.00 until the spa hands
 * over its rate card.
 *
 * The cluster section is REAL as of task 3.2. Each row is a node this one has
 * actually asked, and "last seen" is when it answered rather than when it was
 * tried. Until 19 September this screen carried a note explaining that a second
 * node drawn as offline would be a mock-up rather than evidence. It is now
 * evidence.
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

  /** The node registry (3.2). Loaded separately and never allowed to break the
   *  page, for the same reason as health. */
  cluster = signal<NodeView[]>([]);
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
      const [overview, health, cluster] = await Promise.all([
        this.api.overview(),
        this.api.health().catch(() => null),
        this.api.nodes().catch(() => []),
      ]);
      this.data.set(overview);
      this.health.set(health);
      this.cluster.set(cluster);
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

  /**
   * The state of whichever node OWNS this branch.
   *
   * The branch card used to print ONLINE unconditionally. That was true while
   * one node existed and became a claim nobody had checked the moment there
   * were two - the screen would have drawn a dead branch as healthy, which is
   * the single thing an operations screen must never do.
   */
  stateOf(nodeId: string): 'UNKNOWN' | 'ONLINE' | 'UNREACHABLE' {
    return this.cluster().find(n => n.nodeId === nodeId)?.state ?? 'UNKNOWN';
  }

  /** How long ago, in words. "Last seen" only ever means "it really answered". */
  seen(iso: string | null): string {
    if (!iso) return 'never answered';
    const then = new Date(iso).getTime();
    if (Number.isNaN(then)) return '—';
    const secs = Math.max(0, Math.round((Date.now() - then) / 1000));
    if (secs < 90) return secs + 's ago';
    const mins = Math.round(secs / 60);
    if (mins < 90) return mins + ' min ago';
    return this.when(iso);
  }

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

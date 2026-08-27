import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { ToastService } from '../../../core/toast.service';
import { AuditRow, OpsApi } from '../../../core/ops.api';

/**
 * A5 — the aggregated AuditLog, read from GET /audit-log.
 *
 * Append-only by design: there is no write route and no delete route on the
 * server at all, not merely no button here. An audit trail that can be edited
 * afterwards is not evidence of anything, and a panel can check that claim by
 * looking for the missing endpoints rather than taking the UI's word for it.
 *
 * This is also where the reliability figure comes from. Every assistant reply is
 * written here with what the model proposed and what the server rejected, so
 * "the assistant never offered a contraindicated service" is a query, not a
 * claim (see backend/verify.sql).
 */
@Component({
  selector: 'app-admin-audit',
  imports: [DashShell],
  templateUrl: './audit.html',
  styleUrl: './audit.scss',
})
export class AdminAudit implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(OpsApi);
  protected toast = inject(ToastService);

  rows = signal<AuditRow[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  q = signal('');
  filter = signal('All actions');

  /** Built from what is actually in the log, not a hard-coded list that drifts. */
  filters = computed(() => ['All actions',
    ...Array.from(new Set(this.rows().map(r => r.action))).sort()]);

  shown = computed(() => {
    const term = this.q().trim().toLowerCase();
    const f = this.filter();
    return this.rows().filter(e => {
      const hay = [e.actor, e.action, e.entityType, e.entityId, e.details, e.originNodeId, e.branch]
        .filter(Boolean).join(' ').toLowerCase();
      const passFilter = f === 'All actions' || e.action === f;
      return passFilter && (term === '' || hay.includes(term));
    });
  });

  async ngOnInit(): Promise<void> {
    const action = this.route.snapshot.queryParamMap.get('action');
    if (action) this.filter.set(action);
    await this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.rows.set(await this.api.auditLog(undefined, 500));
    } catch {
      this.error.set('We could not load the audit log.');
    } finally {
      this.loading.set(false);
    }
  }

  when(iso: string): string {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString('en-GB',
      { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
  }

  short(id: string | null): string {
    return id ? id.slice(0, 8).toUpperCase() : '—';
  }

  ok(a: string): boolean {
    return a.endsWith('_CREATED') || a === 'ASSESSMENT_SUBMITTED' || a === 'ASSISTANT_OK';
  }
  bad(a: string): boolean { return a.includes('CANCELLED') || a.includes('REJECTED'); }
  warn(a: string): boolean { return a.includes('PROTOCOL') || a.includes('ROLE') || a.includes('UPDATED'); }
  neutral(a: string): boolean { return !this.ok(a) && !this.bad(a) && !this.warn(a); }

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }

  /** Exports what is on screen. The log itself is never touched. */
  exportCsv(): void {
    const head = ['when', 'actor', 'action', 'entityType', 'entityId', 'branch', 'node', 'details'];
    const body = this.shown().map(r => [
      r.occurredAt, r.actor, r.action, r.entityType, r.entityId,
      r.branch ?? '', r.originNodeId, r.details ?? '',
    ].map(csv).join(','));
    const blob = new Blob([[head.join(','), ...body].join('\r\n')],
      { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `hilotspa-audit-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    this.toast.show(`Exported ${this.shown().length} entries. The log itself is unchanged.`);
  }
}

function csv(v: string): string {
  const s = String(v ?? '');
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

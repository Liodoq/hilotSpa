import { Component, computed, inject, signal } from '@angular/core';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { ToastService } from '../../../core/toast.service';
import { AUDIT } from '../../../core/demo';

/** A5 — aggregated AuditLog. Append-only by design: an audit trail that can be
 *  edited afterwards is not evidence of anything. */
@Component({
  selector: 'app-admin-audit',
  imports: [DashShell],
  templateUrl: './audit.html',
  styleUrl: './audit.scss',
})
export class AdminAudit {
  protected toast = inject(ToastService);
  protected filters = ['All actions', 'CANCELLED', 'ROLE_ASSIGNED', 'PROTOCOL_EDITED', 'bulan-01', 'sorsogon-01'];

  q = signal('');
  filter = signal(this.filters[0]);

  shown = computed(() => {
    const term = this.q().trim().toLowerCase();
    const f = this.filter();
    return AUDIT.filter(e => {
      const hay = (e.who + ' ' + e.action + ' ' + e.record + ' ' + e.node).toLowerCase();
      const passFilter = f === 'All actions' || hay.includes(f.toLowerCase());
      return passFilter && (term === '' || hay.includes(term));
    });
  });

  ok(a: string): boolean { return a === 'BOOKING_CREATED' || a === 'ASSESSMENT_SUBMITTED'; }
  bad(a: string): boolean { return a.includes('CANCELLED'); }
  warn(a: string): boolean { return a === 'PROTOCOL_EDITED' || a === 'ROLE_ASSIGNED'; }
  neutral(a: string): boolean { return !this.ok(a) && !this.bad(a) && !this.warn(a); }

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }
}

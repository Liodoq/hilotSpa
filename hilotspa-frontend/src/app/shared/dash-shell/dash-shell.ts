import { Component, computed, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Logo } from '../logo/logo';
import { Toast } from '../toast/toast';

const STAFF_NAV = [
  { path: '/staff/dashboard', label: 'Dashboard' },
  { path: '/staff/queue',     label: 'Queue' },
  { path: '/staff/resources', label: 'Therapists & rooms' },
  { path: '/staff/report',    label: 'Pre-assessments' },
  { path: '/staff/walkin',    label: 'Record walk-in' },
];
const ADMIN_NAV = [
  { path: '/admin/overview', label: 'Overview' },
  { path: '/admin/branches', label: 'Branches' },
  { path: '/admin/accounts', label: 'Accounts' },
  { path: '/admin/config',   label: 'Configuration' },
  { path: '/admin/audit',    label: 'Audit logs' },
];

/** The back-office chrome: sidebar, scope badge, optional banner. */
@Component({
  selector: 'app-dash-shell',
  imports: [RouterLink, RouterLinkActive, Logo, Toast],
  templateUrl: './dash-shell.html',
  styleUrl: './dash-shell.scss',
})
export class DashShell {
  protected auth = inject(AuthService);

  role = input.required<'STAFF' | 'ADMIN'>();
  scopeNote = input('');
  banner = input<string | null>(null);
  bannerTone = input<'off' | 'ctx'>('off');
  bannerRight = input<string | null>(null);

  nav = computed(() => (this.role() === 'STAFF' ? STAFF_NAV : ADMIN_NAV));
  roleLabel = computed(() => this.role() === 'STAFF' ? 'Front desk · Bulan' : 'Administrator · all branches');
  scopeLabel = computed(() => this.role() === 'STAFF' ? 'BULAN BRANCH' : 'ALL BRANCHES');
  initials = computed(() =>
    (this.auth.fullName() || 'K W').split(' ').filter(Boolean)
      .slice(0, 2).map(p => p[0]).join('').toUpperCase());
}

import { Component, computed, inject, signal } from '@angular/core';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { ToastService } from '../../../core/toast.service';
import { ACCOUNTS } from '../../../core/demo';

/** A3 — the only place a role is ever assigned. Self-registration hardcodes
 *  CUSTOMER, and RegisterRequest has no role field for anyone to abuse. */
@Component({
  selector: 'app-admin-accounts',
  imports: [DashShell],
  templateUrl: './accounts.html',
  styleUrl: './accounts.scss',
})
export class AdminAccounts {
  protected toast = inject(ToastService);
  protected roles = ['All roles', 'STAFF', 'ADMIN', 'CUSTOMER'];

  q = signal('');
  role = signal(this.roles[0]);

  shown = computed(() => {
    const term = this.q().trim().toLowerCase();
    const r = this.role();
    return ACCOUNTS.filter(a =>
      (r === 'All roles' || a.role === r) &&
      (term === '' || (a.name + ' ' + a.email).toLowerCase().includes(term)));
  });

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }
}

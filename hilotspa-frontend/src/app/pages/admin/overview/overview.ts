import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { NODES, TOP_COMPLAINTS } from '../../../core/demo';

/**
 * A1 — node aggregation. Figure 3.3.
 *
 * This is the screen that turns "decentralised" from a claim into something a
 * panelist can look at: one node online and synced, one offline with queued
 * writes and figures explicitly marked stale — still taking bookings locally.
 */
@Component({
  selector: 'app-admin-overview',
  imports: [DashShell],
  templateUrl: './overview.html',
  styleUrl: './overview.scss',
})
export class AdminOverview {
  private router = inject(Router);
  protected nodes = NODES;
  protected complaints = TOP_COMPLAINTS;

  online = computed(() => this.nodes.filter(n => n.online).length);
  totalBookings = computed(() => this.nodes.reduce((a, n) => a + n.bookings, 0));
  totalCollected = computed(() =>
    this.nodes.reduce((a, n) => a + n.collected, 0).toLocaleString('en-PH'));

  open(_branch: string): void { this.router.navigateByUrl('/admin/branches'); }
}

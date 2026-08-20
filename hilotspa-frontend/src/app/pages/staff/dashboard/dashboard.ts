import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { ToastService } from '../../../core/toast.service';
import { QUEUE, STAFF, TODAY } from '../../../core/demo';

/**
 * S1 — Local Branch Dashboard. Figure 3.2: restricted to location-specific data.
 *
 * The offline banner is X1. It is toggleable here on purpose — being able to
 * show a panel that the branch keeps working while disconnected is the whole
 * point of the decentralisation claim, and it should not depend on someone
 * actually unplugging a cable mid-defense.
 */
@Component({
  selector: 'app-staff-dashboard',
  imports: [DashShell, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class StaffDashboard {
  protected toast = inject(ToastService);
  protected today = TODAY;
  protected queue = QUEUE;
  protected staff = STAFF;

  offline = signal(true);
  available = computed(() => this.staff.filter(s => s.status !== 'OFF_DUTY').length);
  unassigned = computed(() => this.today.find(s => !s.therapist)?.time ?? null);

  assign(time: string): void {
    this.toast.show(`Josie Delgado assigned to the ${time} booking`);
  }
}

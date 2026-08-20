import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { ToastService } from '../../../core/toast.service';
import { ROOMS, SERVICES, STAFF } from '../../../core/demo';

/** S5 — staff-entered appointment. BookingSource.STAFF_MANUAL, and the reason
 *  Appointment.form is nullable: a walk-in may never touch the Angular flow. */
@Component({
  selector: 'app-staff-walkin',
  imports: [DashShell, RouterLink],
  templateUrl: './walkin.html',
  styleUrl: './walkin.scss',
})
export class StaffWalkin {
  private router = inject(Router);
  protected toast = inject(ToastService);

  protected services = SERVICES.filter(s => s.suitable);
  protected freeStaff = STAFF.filter(s => s.status === 'AVAILABLE');
  protected freeRooms = ROOMS.filter(r => r.state === 'FREE');

  client = signal('Pedro Santos — 0917 555 1234');
  service = signal(this.services[0].name);
  startAt = signal('2:30 PM, today');
  therapist = signal(this.freeStaff[0]?.name ?? '');
  room = signal(this.freeRooms[0]?.name ?? '');
  notes = signal('');

  price = computed(() => this.services.find(s => s.name === this.service())?.price ?? 0);

  val(ev: Event): string {
    return (ev.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value;
  }

  record(): void {
    this.toast.show(`Walk-in recorded — STAFF_MANUAL, ₱${this.price()} at booking`);
    setTimeout(() => this.router.navigateByUrl('/staff/dashboard'), 800);
  }
}

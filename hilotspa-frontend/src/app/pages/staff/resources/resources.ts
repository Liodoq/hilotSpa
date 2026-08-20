import { Component, inject, signal } from '@angular/core';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { ToastService } from '../../../core/toast.service';
import { DemoRoom, DemoStaff, ROOMS, ROOM_STATES, STAFF, STAFF_STATUSES } from '../../../core/demo';

/** S3 — therapist and room status. Therapists have no login; the front desk
 *  manages them, which is why this screen exists at all. */
@Component({
  selector: 'app-staff-resources',
  imports: [DashShell],
  templateUrl: './resources.html',
  styleUrl: './resources.scss',
})
export class StaffResources {
  protected toast = inject(ToastService);
  protected statuses = STAFF_STATUSES;
  protected roomStates = ROOM_STATES;

  staff = signal<DemoStaff[]>(STAFF.map(s => ({ ...s })));
  rooms = signal<DemoRoom[]>(ROOMS.map(r => ({ ...r })));

  label(v: string): string {
    return v.charAt(0) + v.slice(1).toLowerCase().replace('_', ' ');
  }

  setStatus(id: string, status: DemoStaff['status']): void {
    this.staff.update(list => list.map(s => s.id === id ? { ...s, status } : s));
    const who = this.staff().find(s => s.id === id);
    this.toast.show(`${who?.name} set to ${this.label(status)}`);
  }

  setRoom(id: string, state: DemoRoom['state']): void {
    this.rooms.update(list => list.map(r => r.id === id ? { ...r, state } : r));
    const which = this.rooms().find(r => r.id === id);
    this.toast.show(`${which?.name} set to ${this.label(state)}`);
  }
}

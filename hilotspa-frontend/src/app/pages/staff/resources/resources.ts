import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { BranchContext } from '../../../core/branch-context';
import { ToastService } from '../../../core/toast.service';
import { OpsApi, RoomDto, ScheduleRow, TherapistDto } from '../../../core/ops.api';

type Status = TherapistDto['status'];

/**
 * S3 — therapist and room status, read from and written to the database.
 *
 * Therapists have no login; the front desk manages them, which is why this
 * screen exists at all. Setting someone OFF_DUTY here removes them from every
 * future slot search on this node, because availability() reads the same rows.
 *
 * Room occupancy is DERIVED from today's appointments rather than stored. A
 * stored "occupied" flag is a second source of truth that drifts the moment
 * somebody forgets to clear it — the booking table already knows.
 */
@Component({
  selector: 'app-staff-resources',
  imports: [DashShell],
  templateUrl: './resources.html',
  styleUrl: './resources.scss',
})
export class StaffResources implements OnInit {
  private api = inject(OpsApi);
  /** Null for staff — the server takes their branch from the token. Set only
   *  when an ADMINISTRATOR has switched into a branch (Figure 3.3). */
  protected ctx = inject(BranchContext);
  protected toast = inject(ToastService);

  protected statuses: Status[] = ['AVAILABLE', 'BUSY', 'ON_BREAK', 'OFF_DUTY'];

  staff = signal<TherapistDto[]>([]);
  rooms = signal<RoomDto[]>([]);
  today = signal<ScheduleRow[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  saving = signal<string | null>(null);

  /** Rooms with a session running right now, by room name. */
  private busyRooms = computed(() => {
    const now = Date.now();
    return new Set(this.today()
      .filter(r => new Date(r.start).getTime() <= now && new Date(r.end).getTime() > now)
      .filter(r => r.status !== 'CANCELLED')
      .map(r => r.room));
  });

  available = computed(() => this.staff().filter(s => s.status === 'AVAILABLE').length);

  async ngOnInit(): Promise<void> { await this.load(); }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const b = this.ctx.branchId();
      const [t, r, s] = await Promise.all([
        this.api.therapists(b), this.api.rooms(b), this.api.schedule(undefined, b),
      ]);
      this.staff.set(t);
      this.rooms.set(r);
      this.today.set(s);
    } catch {
      this.error.set('We could not load therapists and rooms. Check the connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }

  label(v: string): string {
    return v.charAt(0) + v.slice(1).toLowerCase().replace(/_/g, ' ');
  }

  name(t: TherapistDto): string { return `${t.firstName} ${t.lastName}`; }

  initials(t: TherapistDto): string {
    return ((t.firstName[0] ?? '') + (t.lastName[0] ?? '')).toUpperCase();
  }

  roomState(r: RoomDto): 'FREE' | 'OCCUPIED' | 'CLOSED' {
    if (!r.active) return 'CLOSED';
    return this.busyRooms().has(r.name) ? 'OCCUPIED' : 'FREE';
  }

  /** Optimistic, then reconciled with what the server actually stored. */
  async setStatus(t: TherapistDto, status: Status): Promise<void> {
    if (t.status === status || this.saving()) return;
    const before = t.status;
    this.patchStaff(t.id, { status });
    this.saving.set(t.id);
    try {
      const saved = await this.api.saveTherapist(t.id, { status });
      this.patchStaff(t.id, saved);
      this.toast.show(`${this.name(t)} set to ${this.label(status)}`);
    } catch {
      this.patchStaff(t.id, { status: before });
      this.toast.show('That did not save. Nothing was changed.', 3200);
    } finally {
      this.saving.set(null);
    }
  }

  /** A closed room is `active = false` — the same flag availability() reads. */
  async setRoomOpen(r: RoomDto, active: boolean): Promise<void> {
    if (r.active === active || this.saving()) return;
    this.patchRoom(r.id, { active });
    this.saving.set(r.id);
    try {
      const saved = await this.api.saveRoom(r.id, { active });
      this.patchRoom(r.id, saved);
      this.toast.show(`${r.name} ${active ? 'reopened' : 'closed'}`);
    } catch {
      this.patchRoom(r.id, { active: !active });
      this.toast.show('That did not save. Nothing was changed.', 3200);
    } finally {
      this.saving.set(null);
    }
  }

  private patchStaff(id: string, patch: Partial<TherapistDto>): void {
    this.staff.update(list => list.map(s => s.id === id ? { ...s, ...patch } : s));
  }

  private patchRoom(id: string, patch: Partial<RoomDto>): void {
    this.rooms.update(list => list.map(r => r.id === id ? { ...r, ...patch } : r));
  }
}

import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { BranchContext } from '../../../core/branch-context';
import { OpsApi, ScheduleRow, TherapistDto } from '../../../core/ops.api';

/**
 * S2 — the walk-in queue. Named explicitly in Figure 3.2.
 *
 * There is no queue table on the server, so this screen does NOT invent one. It
 * shows what genuinely exists — today's booked sessions and who is free — and
 * states plainly what is missing. A screen full of convincing fake names is the
 * fastest way to be asked, at defense, to add one and watch nothing happen.
 *
 * B77 is closed, so a walk-in CAN now be recorded as an appointment. What is
 * still missing is the queue itself: a `walk_in` table holding arrival time and
 * waiting order. An appointment row cannot stand in for that — it records when
 * someone is booked, not how long they have been standing at the counter.
 */
@Component({
  selector: 'app-staff-queue',
  imports: [DashShell, RouterLink],
  templateUrl: './queue.html',
  styleUrl: './queue.scss',
})
export class StaffQueue implements OnInit {
  private api = inject(OpsApi);
  /** Null for staff — the server takes their branch from the token. Set only
   *  when an ADMINISTRATOR has switched into a branch (Figure 3.3). */
  protected ctx = inject(BranchContext);

  today = signal<ScheduleRow[]>([]);
  staff = signal<TherapistDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  free = computed(() => this.staff().filter(s => s.status === 'AVAILABLE'));

  /** Sessions still to come today — who the front desk is actually waiting on. */
  upcoming = computed(() => {
    const now = Date.now();
    return this.today().filter(s => new Date(s.start).getTime() > now);
  });

  async ngOnInit(): Promise<void> { await this.load(); }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const b = this.ctx.branchId();
      const [sched, staff] = await Promise.all([this.api.schedule(undefined, b), this.api.therapists(b)]);
      this.today.set(sched);
      this.staff.set(staff);
    } catch {
      this.error.set('We could not load today\'s sheet.');
    } finally {
      this.loading.set(false);
    }
  }
}

import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { BranchContext } from '../../../core/branch-context';
import { ToastService } from '../../../core/toast.service';
import { OpsApi, ScheduleRow, TherapistDto } from '../../../core/ops.api';

/**
 * S1 — Local Branch Dashboard. Figure 3.2: restricted to location-specific data.
 *
 * Everything here is one day of THIS branch, read from GET /appointments/schedule.
 * The branch is taken from the token on the server, so there is no branch to pass
 * and nothing a staff account could edit to see another location's sheet.
 *
 * The offline banner is X1, and it is still a toggle on purpose: showing a panel
 * that the branch keeps working while disconnected should not depend on someone
 * unplugging a cable mid-defense. It is labelled as a simulation in the UI.
 */
@Component({
  selector: 'app-staff-dashboard',
  imports: [DashShell, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class StaffDashboard implements OnInit {
  private api = inject(OpsApi);
  /** Null for staff — the server takes their branch from the token. Set only
   *  when an ADMINISTRATOR has switched into a branch (Figure 3.3). */
  protected ctx = inject(BranchContext);
  protected toast = inject(ToastService);

  today = signal<ScheduleRow[]>([]);
  staff = signal<TherapistDto[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  offline = signal(false);

  now = signal(Date.now());

  available = computed(() => this.staff().filter(s => s.status === 'AVAILABLE').length);
  onDuty = computed(() => this.staff().filter(s => s.active).length);
  completed = computed(() => this.today().filter(s => s.status === 'COMPLETED').length);
  fromAssistant = computed(() => this.today().filter(s => s.source === 'CHATBOT').length);
  withoutAssessment = computed(() => this.today().filter(s => !s.hasAssessment).length);

  /** Live now / next / done, worked out from the clock rather than stored. */
  state(s: ScheduleRow): 'COMPLETED' | 'IN_SESSION' | 'UPCOMING' {
    if (s.status === 'COMPLETED') return 'COMPLETED';
    const n = this.now();
    if (new Date(s.start).getTime() <= n && new Date(s.end).getTime() > n) return 'IN_SESSION';
    return new Date(s.end).getTime() <= n ? 'COMPLETED' : 'UPCOMING';
  }

  heading = computed(() => new Date(this.now()).toLocaleDateString('en-GB',
    { weekday: 'long', day: 'numeric', month: 'long' }));

  async ngOnInit(): Promise<void> { await this.load(); }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const b = this.ctx.branchId();
      const [sched, staff] = await Promise.all([this.api.schedule(undefined, b), this.api.therapists(b)]);
      this.today.set(sched);
      this.staff.set(staff);
      this.now.set(Date.now());
    } catch {
      this.error.set('We could not load today\'s sheet. Check the connection and try again.');
    } finally {
      this.loading.set(false);
    }
  }
}

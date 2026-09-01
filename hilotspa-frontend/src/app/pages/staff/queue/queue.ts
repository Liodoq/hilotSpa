import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { BranchContext } from '../../../core/branch-context';
import { DayLoad, OpsApi, ScheduleRow, TherapistDto } from '../../../core/ops.api';
import { FormsApi } from '../../../core/forms.api';

/** One square of the rendered grid. Leading blanks are cells too, so the
 *  template never has to run two loops over one row of a calendar. */
interface Cell {
  key: string;          // yyyy-MM-dd, '' for a leading blank
  day: number;
  total: number;
  owed: number;
  today: boolean;
  past: boolean;
}

const DOW = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July',
  'August', 'September', 'October', 'November', 'December'];

/** Local yyyy-MM-dd. Never toISOString() — that is UTC, and in Manila it hands
 *  back yesterday for anything before 8am. */
function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

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
 *
 * ---------------------------------------------------------------------------
 * THE CALENDAR, at the bottom.
 *
 * Everything above it is about today, because until now the whole screen was:
 * it called schedule() with no date and filtered today's rows. That left a real
 * hole. A visit last Tuesday that nobody marked Completed or No show was
 * unreachable from every screen in the system — and an unanswered visit can
 * never collect a painScoreAfter, which is the outcome data the paper rests on.
 * The calendar is how those days are found, which is why they are the one thing
 * on the grid marked in gold.
 */
@Component({
  selector: 'app-staff-queue',
  imports: [DashShell, RouterLink],
  templateUrl: './queue.html',
  styleUrl: './queue.scss',
})
export class StaffQueue implements OnInit {
  private api = inject(OpsApi);
  private bookings = inject(FormsApi);
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

  /**
   * Visits whose time has passed and which nobody has closed off yet.
   *
   * These are the ones the front desk still owes an answer on: did the client
   * come, or not? Nothing else in the system can answer it - the clock knows the
   * hour passed, not whether anyone walked in - and until somebody says, the
   * client cannot be asked how they feel afterwards.
   */
  awaitingOutcome = computed(() => {
    const now = Date.now();
    const open = new Set(['PENDING', 'CONFIRMED', 'IN_PROGRESS']);
    return this.today().filter(s =>
      new Date(s.start).getTime() <= now && open.has(s.status));
  });

  /** The row currently being written, so a double-tap cannot send twice. */
  closing = signal<string | null>(null);

  async close(row: ScheduleRow, attended: boolean): Promise<void> {
    if (this.closing()) return;
    this.closing.set(row.id);
    try {
      if (attended) {
        await this.bookings.completeBooking(row.id);
      } else {
        await this.bookings.noShowBooking(row.id);
      }
      // Re-read the sheet rather than patch the row: the server decides what
      // the status now is, and a screen that shows what it hoped it wrote is
      // the same mistake as B69.
      await this.reread();
    } catch {
      this.error.set('We could not record that. Please try again.');
    } finally {
      this.closing.set(null);
    }
  }

  /**
   * Cancel a booking that has not happened yet.
   *
   * Distinct from "No show", which is for a visit whose time has passed and
   * whom nobody saw. Cancelling is the client ringing ahead; a no-show is
   * silence. Collapsing the two would make the spa's own figures unable to tell
   * a courteous client from an absent one.
   *
   * The row is never deleted - CANCELLED simply is not a blocking status, so
   * the therapist and the room are free again immediately.
   */
  async cancelBooking(row: ScheduleRow): Promise<void> {
    if (this.closing()) return;
    if (this.confirmCancel() !== row.id) {
      // Two taps. A cancel that fires on the first one, on a shared front-desk
      // screen, is a booking lost to a sleeve.
      this.confirmCancel.set(row.id);
      return;
    }
    this.closing.set(row.id);
    try {
      await this.bookings.cancelBooking(row.id, 'Cancelled at the front desk');
      await this.reread();
    } catch {
      this.error.set('We could not cancel that. Please try again.');
    } finally {
      this.closing.set(null);
      this.confirmCancel.set(null);
    }
  }

  /** The row whose cancel is armed and waiting for a second tap. */
  confirmCancel = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    await this.load();
    await this.loadMonth();
  }

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

  /** After any write: today, the month's counts, and the open day if there is
   *  one. Closing off a visit changes all three, and a grid still showing a
   *  gold dot for a day you just cleared is worse than no grid. */
  private async reread(): Promise<void> {
    await this.load();
    await this.loadMonth();
    if (this.picked()) await this.openDay(this.picked()!, true);
  }

  // =========================================================== the calendar

  /** First of the month currently drawn. */
  private viewMonth = signal(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  monthLoad = signal<DayLoad[]>([]);
  monthLoading = signal(false);

  /** yyyy-MM-dd of the open day, or null. */
  picked = signal<string | null>(null);
  pickedRows = signal<ScheduleRow[]>([]);
  pickedLoading = signal(false);

  protected todayKey = iso(new Date());

  monthLabel = computed(() => {
    const v = this.viewMonth();
    return `${MONTHS[v.getMonth()]} ${v.getFullYear()}`;
  });

  /** The busiest day sets the scale, so a quiet month is not drawn as an empty
   *  one. The bar answers "how does this day compare to this month". */
  peak = computed(() => Math.max(1, ...this.monthLoad().map(d => d.total)));

  monthTotal = computed(() => this.monthLoad().reduce((n, d) => n + d.total, 0));
  monthOwedDays = computed(() => this.monthLoad().filter(d => d.owed > 0).length);

  cells = computed<Cell[]>(() => {
    const v = this.viewMonth();
    const byDate = new Map(this.monthLoad().map(d => [d.date, d]));
    const lead = new Date(v.getFullYear(), v.getMonth(), 1).getDay();
    const days = new Date(v.getFullYear(), v.getMonth() + 1, 0).getDate();

    const out: Cell[] = [];
    for (let i = 0; i < lead; i++) {
      out.push({ key: '', day: 0, total: 0, owed: 0, today: false, past: false });
    }
    for (let d = 1; d <= days; d++) {
      const key = iso(new Date(v.getFullYear(), v.getMonth(), d));
      const load = byDate.get(key);
      out.push({
        key, day: d,
        total: load?.total ?? 0,
        owed: load?.owed ?? 0,
        today: key === this.todayKey,
        past: key < this.todayKey,
      });
    }
    return out;
  });

  barWidth(c: Cell): string {
    return c.total ? `${Math.round((c.total / this.peak()) * 100)}%` : '0';
  }

  /** What a screen reader is told about a square. The visual encoding is a bar
   *  and a dot; neither of those is readable aloud. */
  cellLabel(c: Cell): string {
    const v = this.viewMonth();
    let s = `${MONTHS[v.getMonth()]} ${c.day}`;
    s += c.total ? `, ${c.total} session${c.total === 1 ? '' : 's'}` : ', nothing booked';
    if (c.owed) s += `, ${c.owed} not closed off`;
    return s;
  }

  async loadMonth(): Promise<void> {
    const v = this.viewMonth();
    const key = `${v.getFullYear()}-${String(v.getMonth() + 1).padStart(2, '0')}`;
    this.monthLoading.set(true);
    try {
      this.monthLoad.set(await this.api.monthLoad(key, this.ctx.branchId()));
    } catch {
      // The month is a convenience. Losing it must not take today's sheet with
      // it, so this failure is drawn as an empty grid and nothing more.
      this.monthLoad.set([]);
    } finally {
      this.monthLoading.set(false);
    }
  }

  async step(months: number): Promise<void> {
    const v = this.viewMonth();
    this.viewMonth.set(new Date(v.getFullYear(), v.getMonth() + months, 1));
    await this.loadMonth();
  }

  async goToday(): Promise<void> {
    const now = new Date();
    this.viewMonth.set(new Date(now.getFullYear(), now.getMonth(), 1));
    await this.loadMonth();
    await this.openDay(this.todayKey);
  }

  /** Tap a day to open it, tap it again to close it. */
  async openDay(key: string, keepOpen = false): Promise<void> {
    if (!key) return;
    if (!keepOpen && this.picked() === key) { this.picked.set(null); this.pickedRows.set([]); return; }
    this.picked.set(key);
    this.confirmCancel.set(null);
    this.pickedLoading.set(true);
    try {
      this.pickedRows.set(await this.api.schedule(key, this.ctx.branchId()));
    } catch {
      this.pickedRows.set([]);
      this.error.set('We could not open that day.');
    } finally {
      this.pickedLoading.set(false);
    }
  }

  /** Arrow keys move a day or a week, the way a spreadsheet does — which is
   *  the gesture a front desk already has. */
  gridKey(ev: KeyboardEvent): void {
    const step: Record<string, number> = {
      ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7,
    };
    const n = step[ev.key];
    if (n === undefined) return;
    const grid = ev.currentTarget as HTMLElement;
    const cells = Array.from(grid.querySelectorAll<HTMLElement>('.day:not(.blank)'));
    const at = cells.indexOf(document.activeElement as HTMLElement);
    if (at === -1) return;
    ev.preventDefault();
    cells[at + n]?.focus();
  }

  pickedHeading = computed(() => {
    const key = this.picked();
    if (!key) return '';
    const [y, m, d] = key.split('-').map(Number);
    return `${DOW[new Date(y, m - 1, d).getDay()]}, ${d} ${MONTHS[m - 1]}`;
  });

  pickedIsToday = computed(() => this.picked() === this.todayKey);
  pickedIsPast = computed(() => (this.picked() ?? '') < this.todayKey);

  /** Rows on the open day that still owe an answer. */
  pickedOwed = computed(() => {
    const now = Date.now();
    const open = new Set(['PENDING', 'CONFIRMED', 'IN_PROGRESS']);
    return this.pickedRows().filter(s =>
      new Date(s.start).getTime() <= now && open.has(s.status));
  });

  /** Does this row still need a human to say what happened? */
  needsAnswer(row: ScheduleRow): boolean {
    return new Date(row.start).getTime() <= Date.now()
      && ['PENDING', 'CONFIRMED', 'IN_PROGRESS'].includes(row.status);
  }

  /** Booked, not yet due — the only rows that can still be cancelled. */
  stillToCome(row: ScheduleRow): boolean {
    return new Date(row.start).getTime() > Date.now()
      && ['PENDING', 'CONFIRMED', 'IN_PROGRESS'].includes(row.status);
  }
}

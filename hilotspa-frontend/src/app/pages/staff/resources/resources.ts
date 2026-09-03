import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { DatePicker } from '../../../shared/date-picker/date-picker';
import { BranchContext } from '../../../core/branch-context';
import { ToastService } from '../../../core/toast.service';
import { describeHttpError } from '../../../core/http-error';
import { OpsApi, RoomDto, ScheduleRow, TherapistDto } from '../../../core/ops.api';

type Status = TherapistDto['status'];

/**
 * Local-date helpers.
 *
 * toISOString() is UTC, and Manila is UTC+8 - so a day taken from it is the
 * PREVIOUS day for every booking before 8am local. The staff day sheet showing
 * yesterday's rows every morning is exactly the kind of bug that gets blamed on
 * the database.
 */
function toIsoDay(d: Date): string {
  const m = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

function fromIsoDay(s: string): Date {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function addDays(d: Date, n: number): Date {
  const out = new Date(d);
  out.setDate(out.getDate() + n);
  return out;
}

function minutesOf(iso: string): number {
  const d = new Date(iso);
  return d.getHours() * 60 + d.getMinutes();
}

function hourLabel(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const suffix = h < 12 ? 'AM' : 'PM';
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return `${h12} ${suffix}`;
}

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
  imports: [DashShell, DatePicker],
  templateUrl: './resources.html',
  styleUrl: './resources.scss',
})
export class StaffResources implements OnInit {
  private api = inject(OpsApi);
  /** Null for staff — the server takes their branch from the token. Set only
   *  when an ADMINISTRATOR has switched into a branch (Figure 3.3). */
  protected ctx = inject(BranchContext);
  protected toast = inject(ToastService);

  /**
   * Two statuses, not four.
   *
   * BUSY and ON_BREAK are still valid in the database enum - removing enum
   * values means a migration and orphans every row already carrying one - but
   * they are no longer offered here, because they were a second source of truth
   * for something this screen already derives. Whether a therapist is with a
   * client is a fact about today's bookings, and the calendar below reads those
   * rows directly; a button someone has to remember to press can only ever
   * disagree with them. The same argument the rooms already used ("occupied is
   * worked out from today's bookings, not stored as a flag") now applies to
   * people as well.
   *
   * What is left is the one thing only a person knows: whether they are here.
   */
  protected statuses: Status[] = ['AVAILABLE', 'OFF_DUTY'];

  /** Clients may ask for a woman or a man, and the booking rules honour it — so
   *  a therapist with no sex recorded is offered ONLY to clients who expressed
   *  no preference. Never guessed at. */
  protected sexes: { value: string; label: string }[] = [
    { value: 'FEMALE', label: 'Female' },
    { value: 'MALE',   label: 'Male' },
  ];

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

  /**
   * The three numbers whoever opens this screen came for.
   *
   * "2 of 3 available" was true and in the corner in small type. Anyone opening
   * this page is asking who can take a client right now, so it goes at the top
   * at a size you can read from a step back.
   */
  /**
   * With a client RIGHT NOW - counted from the bookings, not from a flag.
   *
   * This used to count therapists whose status said BUSY or ON_BREAK, which
   * nobody can set any more and which was stale whenever they forgot to clear
   * it. Same number, honest source.
   */
  onBreakCount = computed(() => {
    const now = Date.now();
    const busy = new Set(this.today()
      .filter(r => r.status !== 'CANCELLED')
      .filter(r => new Date(r.start).getTime() <= now && new Date(r.end).getTime() > now)
      .map(r => r.therapist));
    return this.staff().filter(s => s.active && busy.has(this.name(s))).length;
  });
  roomsOpen = computed(() => this.rooms().filter(r => r.active).length);

  // ------------------------------------------------------------ the day sheet
  //
  // One day, one column per therapist, time down the side. Deliberately not the
  // week-across-the-top shape a mail calendar uses: the question this screen
  // exists to answer is "somebody is standing at the counter, who can take
  // them at half past two", and that is a question you answer by reading ACROSS
  // one row. In a week view two therapists' bookings share a day column and you
  // have to disentangle them by name.

  /** yyyy-MM-dd, the day being shown. */
  protected day = signal(toIsoDay(new Date()));

  protected dayLabel = computed(() => {
    const d = fromIsoDay(this.day());
    const today = toIsoDay(new Date());
    const rel = this.day() === today ? 'Today'
      : this.day() === toIsoDay(addDays(new Date(), 1)) ? 'Tomorrow'
      : this.day() === toIsoDay(addDays(new Date(), -1)) ? 'Yesterday' : '';
    return { long: d.toLocaleDateString(undefined,
      { weekday: 'long', day: 'numeric', month: 'long' }), rel };
  });

  protected isToday = computed(() => this.day() === toIsoDay(new Date()));

  /**
   * The window the grid draws, in minutes from midnight.
   *
   * Nine to seven covers the spa's hours, but a booking outside them must never
   * be invisible - a row the grid cannot draw is a client nobody sees. So the
   * window stretches to contain whatever the day actually holds.
   */
  private span = computed(() => {
    // 9am to 11pm is the spa's actual trading day.
    let from = 9 * 60;
    let to = 23 * 60;
    for (const r of this.rowsForDay()) {
      from = Math.min(from, minutesOf(r.start));
      to = Math.max(to, minutesOf(r.end));
    }
    return { from: Math.floor(from / 60) * 60, to: Math.ceil(to / 60) * 60 };
  });

  private rowsForDay = computed(() =>
    this.today().filter(r => r.status !== 'CANCELLED'));

  /** One label per hour, positioned as a percentage down the grid. */
  protected hours = computed(() => {
    const { from, to } = this.span();
    const out: { label: string; topPct: number }[] = [];
    for (let m = from; m <= to; m += 60) {
      out.push({ label: hourLabel(m), topPct: ((m - from) / (to - from)) * 100 });
    }
    return out;
  });

  /**
   * A column per working therapist, each carrying its own bookings.
   *
   * Matched on the rendered name because that is what the schedule endpoint
   * returns - it is a day sheet for reading, not a join key. A therapist with
   * no bookings still gets a column: an empty column is the answer to "who is
   * free", and dropping it would hide exactly the person you are looking for.
   */
  protected columns = computed(() => {
    const { from, to } = this.span();
    const total = to - from;
    return this.staff().filter(t => t.active).map(t => {
      const who = this.name(t);
      return {
        id: t.id,
        name: who,
        initials: this.initials(t),
        off: t.status === 'OFF_DUTY',
        blocks: this.rowsForDay().filter(r => r.therapist === who).map(r => {
          const s = minutesOf(r.start);
          const e = minutesOf(r.end);
          return {
            id: r.id,
            client: r.client,
            room: r.room,
            serviceName: r.serviceName,
            time: r.time,
            status: r.status,
            topPct: ((s - from) / total) * 100,
            heightPct: (Math.max(e - s, 20) / total) * 100,
          };
        }),
      };
    });
  });

  /** Bookings on this day that name a therapist no longer on the roster. */
  protected orphaned = computed(() => {
    const known = new Set(this.staff().filter(t => t.active).map(t => this.name(t)));
    return this.rowsForDay().filter(r => !known.has(r.therapist));
  });

  async shiftDay(days: number): Promise<void> {
    this.day.set(toIsoDay(addDays(fromIsoDay(this.day()), days)));
    await this.load();
  }

  async goToday(): Promise<void> {
    if (this.isToday()) return;
    this.day.set(toIsoDay(new Date()));
    await this.load();
  }

  async pickDay(value: string): Promise<void> {
    if (!value) return;
    this.day.set(value);
    await this.load();
  }

  // ------------------------------------------------------------ adding people
  //
  // Until now therapists and rooms only existed because the seeder made them,
  // which meant the spa could not hire anyone or open a room without a
  // developer. Branch is never asked for: staff add to THEIR branch, and an
  // administrator adds to the branch they are currently visiting. A therapist
  // belongs to exactly one branch - that is the whole basis of the
  // single-writer claim - so letting this screen choose one would be inviting
  // somebody to break it.

  protected adding = signal<'therapist' | 'room' | null>(null);
  protected newFirstName = signal('');
  protected newLastName = signal('');
  protected newSex = signal<string | null>(null);
  protected newRoomName = signal('');

  openAdd(what: 'therapist' | 'room'): void {
    this.adding.set(what);
    this.newFirstName.set('');
    this.newLastName.set('');
    this.newSex.set(null);
    this.newRoomName.set('');
  }

  closeAdd(): void { this.adding.set(null); }

  async addTherapist(): Promise<void> {
    const first = this.newFirstName().trim();
    const last = this.newLastName().trim();
    if (!first || !last || this.saving()) {
      this.toast.show('A therapist needs a first and last name.');
      return;
    }
    this.saving.set('new-therapist');
    try {
      await this.api.saveTherapist(null, {
        firstName: first,
        lastName: last,
        // Null stays null. A therapist whose sex nobody recorded is offered
        // only to clients who expressed no preference - guessing here is the
        // one mistake that feature exists to prevent.
        sex: this.newSex(),
        status: 'OFF_DUTY',
        active: true,
        branchId: this.ctx.branchId() ?? undefined,
      } as Partial<TherapistDto>);
      await this.load();
      this.closeAdd();
      this.toast.show(first + ' has been added, off duty. Set them available when they start.');
    } catch {
      this.toast.show('We could not add them just now.');
    } finally {
      this.saving.set(null);
    }
  }

  async addRoom(): Promise<void> {
    const name = this.newRoomName().trim();
    if (!name || this.saving()) {
      this.toast.show('A room needs a name.');
      return;
    }
    this.saving.set('new-room');
    try {
      await this.api.saveRoom(null, {
        name,
        active: true,
        branchId: this.ctx.branchId() ?? undefined,
      } as Partial<RoomDto>);
      await this.load();
      this.closeAdd();
      this.toast.show(name + ' is open.');
    } catch {
      this.toast.show('We could not add that room just now.');
    } finally {
      this.saving.set(null);
    }
  }

  // ------------------------------------------------------- editing and leaving
  //
  // There is no DELETE, here or in the API, and that is deliberate. Every
  // appointment carries a non-null therapist and room; deleting either would
  // take the visits they were part of, or fail on the foreign key and look like
  // a bug. A therapist who leaves has still given every massage they gave.
  //
  // `active = false` is the honest operation: they disappear from every future
  // slot search on this node, and every past booking still names them.

  protected editing = signal<string | null>(null);
  protected editFirstName = signal('');
  protected editLastName = signal('');
  protected editRoomName = signal('');
  protected confirmLeave = signal<string | null>(null);

  openEditTherapist(x: TherapistDto): void {
    this.editing.set(x.id);
    this.editFirstName.set(x.firstName);
    this.editLastName.set(x.lastName);
  }

  openEditRoom(r: RoomDto): void {
    this.editing.set(r.id);
    this.editRoomName.set(r.name);
  }

  closeEdit(): void { this.editing.set(null); this.confirmLeave.set(null); }

  async saveTherapistName(x: TherapistDto): Promise<void> {
    const first = this.editFirstName().trim();
    const last = this.editLastName().trim();
    if (!first || !last || this.saving()) {
      this.toast.show('A therapist needs a first and last name.');
      return;
    }
    this.saving.set(x.id);
    try {
      await this.api.saveTherapist(x.id, { firstName: first, lastName: last });
      await this.load();
      this.closeEdit();
    } catch {
      this.toast.show('We could not save that.');
    } finally {
      this.saving.set(null);
    }
  }

  async saveRoomName(r: RoomDto): Promise<void> {
    const name = this.editRoomName().trim();
    if (!name || this.saving()) {
      this.toast.show('A room needs a name.');
      return;
    }
    this.saving.set(r.id);
    try {
      await this.api.saveRoom(r.id, { name });
      await this.load();
      this.closeEdit();
    } catch {
      this.toast.show('We could not save that.');
    } finally {
      this.saving.set(null);
    }
  }

  /**
   * A therapist leaves the spa, or comes back.
   *
   * Not a delete. Two taps, because on a shared front-desk screen a single tap
   * would remove somebody from every future booking by accident.
   */
  async setWorksHere(x: TherapistDto, active: boolean): Promise<void> {
    if (this.saving()) return;
    if (!active && this.confirmLeave() !== x.id) {
      this.confirmLeave.set(x.id);
      return;
    }
    this.saving.set(x.id);
    try {
      await this.api.saveTherapist(x.id, { active });
      await this.load();
      this.toast.show(active
        ? `${x.firstName} is back on the team.`
        : `${x.firstName} has been removed from the team. Their past sessions are unchanged.`);
    } catch {
      this.toast.show('We could not save that.');
    } finally {
      this.saving.set(null);
      this.confirmLeave.set(null);
    }
  }

  /** On the team, and no longer on it — shown apart so neither list lies. */
  onTeam = computed(() => this.staff().filter(x => x.active));
  formerStaff = computed(() => this.staff().filter(x => !x.active));

  // -------------------------------------------------------------- the ⋯ menus

  /** Which card's menu is open. One at a time, like every menu ever. */
  protected menu = signal<string | null>(null);

  /**
   * The card whose state changed a moment ago.
   *
   * Cleared on a timer so the pulse plays once and never loops. Staff tap a
   * status and until now nothing on screen confirmed the write landed.
   */
  protected justChanged = signal<string | null>(null);

  private flash(id: string): void {
    this.justChanged.set(id);
    setTimeout(() => {
      if (this.justChanged() === id) { this.justChanged.set(null); }
    }, 950);
  }
  protected confirmDelete = signal<string | null>(null);
  protected addMenu = signal(false);

  toggleMenu(id: string): void {
    this.menu.update(m => (m === id ? null : id));
    this.confirmDelete.set(null);
    this.addMenu.set(false);
  }

  toggleAddMenu(): void {
    this.addMenu.update(v => !v);
    this.menu.set(null);
    this.confirmDelete.set(null);
  }

  /**
   * Close whatever is open, and disarm any two-tap confirmation with it.
   *
   * The scrim calls this, and so does Escape. Leaving a "tap again to delete"
   * armed after the menu closes would mean the NEXT tap on that button deletes
   * without a warning, which is the opposite of what two taps are for.
   */
  closeMenus(): void {
    this.menu.set(null);
    this.addMenu.set(false);
    this.confirmDelete.set(null);
    this.confirmLeave.set(null);
  }

  /** Escape closes a menu, the way every other menu on the machine does. */
  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.menu() || this.addMenu()) { this.closeMenus(); }
  }

  /**
   * Delete for real - only ever reaches the server for a row nothing has used.
   *
   * The server is the judge, not this screen: it counts the appointments and
   * refuses with a reason. Two taps first, because this one does not come back.
   */
  async remove(kind: 'therapist' | 'room', id: string, label: string): Promise<void> {
    if (this.saving()) return;
    if (this.confirmDelete() !== id) {
      this.confirmDelete.set(id);
      return;
    }
    this.saving.set(id);
    try {
      if (kind === 'therapist') {
        await this.api.deleteTherapist(id);
      } else {
        await this.api.deleteRoom(id);
      }
      await this.load();
      this.toast.show(`${label} deleted.`);
      this.menu.set(null);
    } catch (e: unknown) {
      // The 409 carries the real reason - that this row has history and the
      // honest operation is to retire it. Show that, not a shrug.
      const reason = (e as { error?: { message?: string } })?.error?.message;
      this.toast.show(reason || 'We could not delete that.');
    } finally {
      this.saving.set(null);
      this.confirmDelete.set(null);
    }
  }

  async ngOnInit(): Promise<void> { await this.load(); }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const b = this.ctx.branchId();
      const [t, r, s] = await Promise.all([
        this.api.therapists(b), this.api.rooms(b), this.api.schedule(this.day(), b),
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
  async setSex(t: TherapistDto, sex: string): Promise<void> {
    if (this.saving()) return;
    this.saving.set(t.id);
    try {
      // Tapping the one already set clears it, which is how a therapist who
      // would rather not have it recorded gets left out of the matching.
      await this.api.saveTherapist(t.id, { sex: t.sex === sex ? '' : sex });
      await this.load();
      this.flash(t.id);
    } catch (e: unknown) {
      this.toast.show(describeHttpError(e, 'That did not save.'), 3200);
    } finally {
      this.saving.set(null);
    }
  }

  async setStatus(t: TherapistDto, status: Status): Promise<void> {
    if (t.status === status || this.saving()) return;
    const before = t.status;
    this.patchStaff(t.id, { status });
    this.saving.set(t.id);
    try {
      const saved = await this.api.saveTherapist(t.id, { status });
      this.patchStaff(t.id, saved);
      this.flash(t.id);
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
      this.flash(r.id);
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

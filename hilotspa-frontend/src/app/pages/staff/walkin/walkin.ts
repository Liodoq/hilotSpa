import { Component, HostListener, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { BranchContext } from '../../../core/branch-context';
import { AuthService } from '../../../core/auth.service';
import { ToastService } from '../../../core/toast.service';
import { CatalogueEntry, OpsApi, RoomDto, TherapistDto } from '../../../core/ops.api';
import { Openings } from '../../../core/models';
import { DatePicker } from '../../../shared/date-picker/date-picker';
import { priceLabel } from '../../../core/catalogue.store';
import { describeHttpError } from '../../../core/http-error';

/**
 * S5 — the staff-entered appointment. BookingSource.STAFF_MANUAL.
 *
 * A walk-in has no account, which is why B77 had to be settled in the schema
 * before this screen could do anything: `Appointment.customer` is nullable now
 * and a `walkInName` identifies the visit instead. No User row is invented at
 * the counter — an account that cannot log in would be a false entry in the
 * accounts screen.
 *
 * No pre-assessment is required here. Process Rule #2 gates the ASSISTANT
 * behind a completed assessment, not the front desk; refusing to record a
 * paying client who has not filled in a questionnaire would be the app
 * obstructing the business it is meant to serve.
 *
 * The therapist and room are assigned by the server, in the same transaction
 * the assistant uses — so the counter and the chatbot cannot award the same
 * therapist.
 */
@Component({
  selector: 'app-staff-walkin',
  imports: [DashShell, RouterLink, DatePicker],
  templateUrl: './walkin.html',
  styleUrl: './walkin.scss',
})
export class StaffWalkin implements OnInit {
  private api = inject(OpsApi);
  /** Null for staff — the server takes their branch from the token. Set only
   *  when an ADMINISTRATOR has switched into a branch (Figure 3.3). */
  protected ctx = inject(BranchContext);
  private auth = inject(AuthService);

  /**
   * An administrator viewing ALL branches cannot record a walk-in, and should
   * not be shown a form that will be refused.
   *
   * A visit belongs to exactly one branch, and so do the therapist and the room
   * it consumes - that is the property the whole single-writer claim rests on.
   * An administrator has no branch of their own, so until they open one there is
   * no honest answer to "which branch is this walk-in at", and the server says
   * so with a 400. Letting them fill the form first and refusing at the end
   * wastes the typing and reads as a broken screen rather than a missing step.
   *
   * Staff are never in this state: their branch comes from the token.
   */
  protected needsBranch = computed(() =>
    this.auth.role() === 'ADMIN' && !this.ctx.branchId());
  private router = inject(Router);
  protected toast = inject(ToastService);
  protected priceLabel = priceLabel;

  services = signal<CatalogueEntry[]>([]);
  staff = signal<TherapistDto[]>([]);
  rooms = signal<RoomDto[]>([]);
  loading = signal(true);
  saving = signal(false);
  error = signal<string | null>(null);

  name = signal('');
  contact = signal('');
  serviceId = signal('');
  date = signal(todayIso());
  time = signal(nextHalfHour());
  notes = signal('');

  freeStaff = computed(() => this.staff().filter(s => s.status === 'AVAILABLE'));
  freeRooms = computed(() => this.rooms().filter(r => r.active));
  chosen = computed(() => this.services().find(s => s.serviceId === this.serviceId()));

  /** The server refuses these too — this only saves the client a round trip. */
  canSave = computed(() =>
    !!this.name().trim() && !!this.serviceId() && !!this.date() && !!this.time());

  /** Nobody free means the server will answer 409, so say it before they type. */
  blocked = computed(() => !this.freeStaff().length || !this.freeRooms().length);

  // ---------------------------------------------- who and where, at the counter
  //
  // "Free right now" was the whole roster minus the off-duty, which answers a
  // different question from the one being asked: somebody can be on duty and
  // still be with a client at half past two. The counter now asks the server who
  // is free AT THE TIME BEING BOOKED, and a name that is not free cannot be
  // picked.
  //
  // Both stay optional. Null means "whoever is free" - the default, and what
  // most walk-ins want. Naming somebody is for when the client asked, or when
  // the person is standing right there.

  openings = signal<Openings | null>(null);
  checking = signal(false);
  pickedTherapist = signal<string | null>(null);
  pickedRoom = signal<string | null>(null);

  private freeTherapistIds = computed(() =>
    new Set((this.openings()?.therapists ?? []).map(t => t.id)));
  private freeRoomIds = computed(() =>
    new Set((this.openings()?.rooms ?? []).map(r => r.id)));

  /**
   * Everyone on the roster, each carrying whether they are free THEN.
   *
   * Taken people are greyed, not hidden. A name that vanishes makes staff
   * wonder whether they mistyped; a name that is visibly unavailable answers
   * the question they were about to ask out loud.
   */
  /**
   * Only the branch's own people, never the whole business.
   *
   * therapists(branchId) returns EVERY therapist when branchId is null, which
   * is what an administrator who has not opened a branch sends - so a Bulan
   * walk-in form was offering Sorsogon staff. They could never be free (the
   * openings lookup is scoped to one branch) so they sat there permanently
   * greyed, and worse, they were being offered at all: a therapist belongs to
   * exactly one branch, and that is the entire basis of the claim that two
   * nodes cannot award the same slot.
   */
  private ofThisBranch = computed(() => {
    const b = this.ctx.branchId();
    return this.staff().filter(t => t.active && (!b || t.branchId === b));
  });

  therapistChoices = computed(() => this.ofThisBranch()

    .map(t => ({
      id: t.id,
      name: `${t.firstName} ${t.lastName}`,
      free: this.freeTherapistIds().has(t.id),
      // WHY, not just "no". The first version printed "booked" for every
      // unavailable person, so a therapist who was merely off duty looked like
      // one with a client - and the front desk had no way to tell that the fix
      // was one tap on the resources screen rather than a different time.
      //
      // BUSY and ON_BREAK still occur on rows created before those buttons were
      // removed, and a status nobody can set any more is exactly the kind of
      // thing that must name itself when it blocks something.
      why: t.status === 'OFF_DUTY' ? 'off duty'
         : t.status === 'BUSY' ? 'with a client'
         : t.status === 'ON_BREAK' ? 'on a break'
         : 'booked',
      fixable: t.status !== 'AVAILABLE',
    })));

  /** Anyone the roster is blocking rather than the calendar. */
  protected blockedByStatus = computed(() =>
    this.therapistChoices().filter(t => !t.free && t.fixable));

  roomChoices = computed(() => this.rooms()
    .filter(r => r.active && (!this.ctx.branchId() || r.branchId === this.ctx.branchId()))
    .map(r => ({ id: r.id, name: r.name, free: this.freeRoomIds().has(r.id) })));

  /**
   * Ask the server who is free, whenever the treatment or the time changes.
   *
   * Duration is part of the question - a 90-minute booking at 2pm collides with
   * things a 60-minute one does not - so this cannot be keyed on the time alone.
   */
  async refreshOpenings(): Promise<void> {
    const svc = this.serviceId();
    if (this.needsBranch()) { this.openings.set(null); return; }
    if (!svc || !this.date() || !this.time()) { this.openings.set(null); return; }
    this.checking.set(true);
    try {
      this.openings.set(
        await this.api.counterOpenings(
          svc, `${this.date()}T${this.time()}:00`, this.ctx.branchId()));

      // A pick that has just gone stops being a pick. Clearing it silently is
      // right HERE, where the chip greys out in front of them; it would be
      // wrong at the moment of booking, where they have already said a name out
      // loud - which is why the server refuses there instead of substituting.
      if (this.pickedTherapist() && !this.freeTherapistIds().has(this.pickedTherapist()!)) {
        this.pickedTherapist.set(null);
      }
      if (this.pickedRoom() && !this.freeRoomIds().has(this.pickedRoom()!)) {
        this.pickedRoom.set(null);
      }
    } catch {
      // Not something the front desk needs to see: the picker falls back to
      // "whoever is free", which is exactly what it did before this existed.
      this.openings.set(null);
    } finally {
      this.checking.set(false);
    }
  }

  /**
   * Re-ask whenever the question changes - derived from the SIGNALS, not from
   * DOM events.
   *
   * The first version hung a handler on each of the three inputs, and the list
   * then went stale the moment anything set one of those signals by another
   * route: the date picker's own output, a default applied on load, a
   * programmatic reset. The screen showed Ernesto as booked on a date nobody
   * had checked, which is worse than showing nothing - staff would have turned
   * a client away on it.
   *
   * An effect cannot be bypassed. Whoever writes the signal, the question is
   * asked again.
   */
  private readonly watchQuestion = effect(() => {
    const svc = this.serviceId();
    const date = this.date();
    const time = this.time();
    // untracked so the signals refreshOpenings() WRITES do not re-trigger this.
    untracked(() => { if (svc && date && time) void this.refreshOpenings(); });
  });

  /** Half-hour starts across the working day. */
  protected timeOptions = (() => {
    const out: { value: string; label: string }[] = [];
    // The spa trades 9am to 11pm. Not a guess and not "business hours" - a walk-in
    // typed in at ten at night is an ordinary evening here.
    for (let m = 9 * 60; m <= 23 * 60; m += 30) {
      const h = Math.floor(m / 60);
      const mm = m % 60 === 0 ? '00' : '30';
      const h12 = h % 12 === 0 ? 12 : h % 12;
      out.push({
        value: `${`${h}`.padStart(2, '0')}:${mm}`,
        label: `${h12}:${mm} ${h < 12 ? 'AM' : 'PM'}`,
      });
    }
    return out;
  })();

  protected timeLabel = computed(() =>
    this.timeOptions.find(o => o.value === this.time())?.label ?? this.time());

  protected timeMenu = signal(false);
  toggleTimeMenu(ev: Event): void { ev.stopPropagation(); this.timeMenu.update(v => !v); }
  pickTime(v: string): void { this.timeMenu.set(false); this.time.set(v); }

  @HostListener('document:click')
  closeTimeMenu(): void { if (this.timeMenu()) this.timeMenu.set(false); }

  @HostListener('document:keydown.escape')
  onEscape(): void { if (this.timeMenu()) this.timeMenu.set(false); }

  /** What the greyed-out chips are actually describing. */
  protected checkedFor = computed(() => {
    const o = this.openings();
    return o ? o.label : null;
  });

  async ngOnInit(): Promise<void> { await this.load(); }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const b = this.ctx.branchId();
      const [cat, staff, rooms] = await Promise.all([
        this.api.catalogue(), this.api.therapists(b), this.api.rooms(b),
      ]);
      this.services.set(cat);
      this.staff.set(staff);
      this.rooms.set(rooms);
      if (!this.serviceId() && cat.length) this.serviceId.set(cat[0].serviceId);
      await this.refreshOpenings();
    } catch {
      this.error.set('We could not load the menu, therapists and rooms.');
    } finally {
      this.loading.set(false);
    }
  }

  val(ev: Event): string {
    return (ev.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value;
  }

  async record(): Promise<void> {
    if (!this.canSave() || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    try {
      // Sent without a timezone offset on purpose. The server works in the
      // spa's timezone; a browser offset would silently shift the booking.
      await this.api.bookWalkIn({
        serviceId: this.serviceId(),
        start: `${this.date()}T${this.time()}:00`,
        name: this.name().trim(),
        contact: this.contact().trim() || null,
        notes: this.notes().trim() || null,
        therapistId: this.pickedTherapist(),
        roomId: this.pickedRoom(),
        branchId: this.ctx.branchId(),
        idempotencyKey: `walkin-${this.name().trim()}-${this.date()}T${this.time()}`,
      });
      this.toast.show(
        `Recorded — ${this.name().trim()}, ${this.chosen()?.name}. Source STAFF_MANUAL.`, 3400);
      setTimeout(() => this.router.navigateByUrl('/staff/dashboard'), 700);
    } catch (e: unknown) {
      this.error.set(describeHttpError(e,
        'We could not record that. Nothing was saved.'));
    } finally {
      this.saving.set(false);
    }
  }
}

/**
 * Today, in the SPA'S day - not UTC's.
 *
 * toISOString() is UTC, and Manila is UTC+8. Before 8am local the UTC date is
 * still YESTERDAY, so the walk-in form opened on the wrong day every single
 * morning - and quietly, because a date field showing a plausible date gives
 * you nothing to notice. A visit typed in at 7am would have been recorded
 * against the previous day, which is the kind of error nobody finds until they
 * are reconciling a week.
 *
 * The staff day sheet and the visit log already build dates from the local
 * getFullYear/getMonth/getDate for exactly this reason. This one was missed.
 */
function todayIso(): string {
  const d = new Date();
  const m = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

/**
 * The next half hour, CLAMPED into the trading day.
 *
 * Unclamped this returned whatever the wall clock said - 00:30 if somebody had
 * the screen open at a quarter past midnight - and that is not a value the time
 * menu contains, so the control showed a start nobody could have selected and
 * the availability lookup went off asking who was free at half past midnight.
 *
 * A default outside the range of its own control is worse than a wrong default:
 * it cannot be corrected by using the control normally.
 */
function nextHalfHour(): string {
  const d = new Date();
  d.setMinutes(d.getMinutes() > 30 ? 60 : 30, 0, 0);
  const minutes = Math.min(Math.max(d.getHours() * 60 + d.getMinutes(), 9 * 60), 23 * 60);
  const h = Math.floor(minutes / 60);
  return `${String(h).padStart(2, '0')}:${minutes % 60 === 0 ? '00' : '30'}`;
}

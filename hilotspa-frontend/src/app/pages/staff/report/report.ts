import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { BranchContext } from '../../../core/branch-context';
import { BodyMap } from '../../../shared/body-map/body-map';
import { ToastService } from '../../../core/toast.service';
import { FormsApi } from '../../../core/forms.api';
import { OpsApi, CatalogueEntry, ScheduleRow } from '../../../core/ops.api';
import { FormsModel, PatientIntakeModel } from '../../../core/models';
import { PainPoint, toPainPoint } from '../../../core/assessment.store';

function isoDay(d: Date): string {
  const m = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

function fromIso(s: string): Date {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function addDays(d: Date, n: number): Date {
  const out = new Date(d);
  out.setDate(out.getDate() + n);
  return out;
}

/** Weeks start Monday. getDay() calls Sunday 0, which would split a weekend. */
function mondayOf(d: Date): Date {
  const out = new Date(d);
  out.setDate(out.getDate() - ((out.getDay() + 6) % 7));
  out.setHours(0, 0, 0, 0);
  return out;
}

/**
 * S4 — the practitioner's view. Figure 3.2 calls this out specifically.
 *
 * This is the digital half of the bone setter's own sheet: the marked regions,
 * L/R, and BEFORE / AFTER pain, read from the real assessment.
 *
 * AFTER is deliberately empty until the session ends. It is written by staff,
 * never by the client — PUT /patient-intake/{id}/after sits behind the STAFF
 * role, so there is no client-facing route to it at all. BEFORE minus AFTER is
 * the only quantitative outcome measure the spa already collects (§H3).
 */
@Component({
  selector: 'app-staff-report',
  imports: [DashShell, BodyMap, RouterLink],
  templateUrl: './report.html',
  styleUrl: './report.scss',
})
export class StaffReport implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(FormsApi);
  private ops = inject(OpsApi);
  /**
   * Null for staff - the server takes their branch from the token and ignores
   * anything the request says. Set only when an ADMINISTRATOR has opened a
   * branch, and it MUST be passed: schedule() with a null branch returns
   * findAll() for an administrator, so omitting it showed Bulan's visits under
   * a header reading "visible to this branch only".
   *
   * Same shape as B101 - a query that widens when the caller happens to be an
   * administrator - and the same lesson: branch scoping is not something a
   * screen may forget to ask for.
   */
  protected ctx = inject(BranchContext);
  protected toast = inject(ToastService);

  form = signal<FormsModel | null>(null);
  today = signal<ScheduleRow[]>([]);

  // ------------------------------------------------------------- the visit log
  //
  // This list used to be "today's pre-assessments": one day, and only the
  // bookings that happened to carry an assessment. That answers "who is coming
  // in later" - which the queue already answers - and hides the thing a front
  // desk actually looks back for, which is what happened. A visit that was
  // cancelled, or that nobody closed off, was invisible on every screen we
  // ship.
  //
  // A week, every visit, with its outcome. Assembled from seven day calls
  // rather than a range endpoint: seven small parallel requests need no backend
  // change, no rebuild and no migration, and this is not a screen anyone loads
  // in a loop. If it ever feels slow, the fix is one endpoint, not a cache.

  weekRows = signal<ScheduleRow[]>([]);
  /** Monday of the week being shown, yyyy-MM-dd. */
  weekStart = signal(isoDay(mondayOf(new Date())));

  protected weekLabel = computed(() => {
    const a = fromIso(this.weekStart());
    const b = addDays(a, 6);
    const same = a.getMonth() === b.getMonth();
    const fmt = (d: Date, month: boolean) => d.toLocaleDateString(undefined,
      month ? { day: 'numeric', month: 'short' } : { day: 'numeric' });
    return `${fmt(a, !same)} – ${fmt(b, true)}`;
  });

  protected isThisWeek = computed(() =>
    this.weekStart() === isoDay(mondayOf(new Date())));

  /** The last twelve weeks, for the dropdown. */
  protected weekOptions = computed(() => {
    const base = mondayOf(new Date());
    return Array.from({ length: 12 }, (_, i) => {
      const m = addDays(base, -7 * i);
      const end = addDays(m, 6);
      return {
        value: isoDay(m),
        label: i === 0 ? 'This week' : i === 1 ? 'Last week'
          : `${m.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })} – `
            + `${end.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}`,
      };
    });
  });

  /**
   * The week grouped into days, newest first, empty days dropped.
   *
   * Newest first because you come to a log to see what just happened. Empty
   * days are omitted rather than shown as blanks - seven headings with nothing
   * under five of them reads as a broken screen, not a quiet week.
   */
  protected logDays = computed(() => {
    const byDay = new Map<string, ScheduleRow[]>();
    for (const r of this.weekRows()) {
      const key = isoDay(new Date(r.start));
      (byDay.get(key) ?? byDay.set(key, []).get(key)!).push(r);
    }
    return [...byDay.entries()]
      .sort((a, b) => b[0].localeCompare(a[0]))
      .map(([iso, rows]) => ({
        iso,
        label: fromIso(iso).toLocaleDateString(undefined,
          { weekday: 'long', day: 'numeric', month: 'long' }),
        isToday: iso === isoDay(new Date()),
        rows: rows.sort((x, y) => x.start.localeCompare(y.start)),
      }));
  });

  protected weekCounts = computed(() => {
    const rows = this.weekRows();
    const now = Date.now();
    return {
      total: rows.length,
      done: rows.filter(r => r.status === 'COMPLETED').length,
      off: rows.filter(r => r.status === 'CANCELLED' || r.status === 'NO_SHOW').length,
      // Past, and nobody closed it off. These rows are the reason this screen
      // is worth building: they exist and no other screen names them.
      owed: rows.filter(r => r.status === 'CONFIRMED'
        && new Date(r.end).getTime() < now).length,
    };
  });

  /** What a person should read. Never the enum. */
  outcome(r: ScheduleRow): string {
    if (r.status === 'COMPLETED') return 'Completed';
    if (r.status === 'CANCELLED') return 'Cancelled';
    if (r.status === 'NO_SHOW') return 'Did not attend';
    return new Date(r.end).getTime() < Date.now() ? 'Not recorded' : 'Booked';
  }

  outcomeTone(r: ScheduleRow): string {
    if (r.status === 'COMPLETED') return 'ok';
    if (r.status === 'CANCELLED' || r.status === 'NO_SHOW') return 'bad';
    return new Date(r.end).getTime() < Date.now() ? 'warn' : 'mute';
  }

  async shiftWeek(weeks: number): Promise<void> {
    this.weekStart.set(isoDay(addDays(fromIso(this.weekStart()), weeks * 7)));
    await this.loadWeek();
  }

  /**
   * The week menu.
   *
   * A <select> was the quick answer and it was the wrong one: its open list is
   * browser chrome - grey highlight, system font, no relation to anything else
   * on the page - and no stylesheet can reach inside it. Same reason the day
   * picker stopped being <input type="date">.
   *
   * Closing is handled three ways because a menu that only closes on selection
   * traps somebody who opened it by accident: Escape, a click anywhere else,
   * and choosing a week.
   */
  protected weekMenu = signal(false);

  toggleWeekMenu(ev: Event): void {
    ev.stopPropagation();
    this.weekMenu.update(v => !v);
  }

  @HostListener('document:click')
  closeWeekMenu(): void { if (this.weekMenu()) this.weekMenu.set(false); }

  @HostListener('document:keydown.escape')
  onEscape(): void { if (this.weekMenu()) this.weekMenu.set(false); }

  async pickWeek(value: string): Promise<void> {
    this.weekMenu.set(false);
    if (!value) return;
    this.weekStart.set(value);
    await this.loadWeek();
  }

  async loadWeek(): Promise<void> {
    this.loading.set(true);
    try {
      const monday = fromIso(this.weekStart());
      const days = Array.from({ length: 7 }, (_, i) => isoDay(addDays(monday, i)));
      const results = await Promise.all(
        days.map(d => this.ops.schedule(d, this.ctx.branchId())
          .catch(() => [] as ScheduleRow[])));
      this.weekRows.set(results.flat());
    } finally {
      this.loading.set(false);
    }
  }
  catalogue = signal<CatalogueEntry[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  saving = signal<string | null>(null);
  notes = signal('');

  /** The marks, in the shape BodyMap draws. */
  points = computed<PainPoint[]>(() =>
    (this.form()?.painPoints ?? []).map(toPainPoint));

  findings = computed(() => (this.form()?.painPoints ?? []).map(p => ({
    id: p.id ?? '',
    region: label(p.anatomicalRegion),
    side: p.side ?? 'CENTRE',
    complaint: p.complaintType ? label(p.complaintType) : '—',
    before: p.painScoreBefore,
    after: p.painScoreAfter ?? null,
  })));

  /** The session this assessment belongs to, if one is booked today. */
  session = computed(() => this.today().find(s => s.formId === this.form()?.id) ?? null);

  /** Today's sessions that HAVE an assessment - the list to choose from. */
  choices = computed(() => this.today().filter(s => s.hasAssessment));

  excluded = computed(() => this.catalogue().filter(c => !c.suitable));
  indicated = computed(() => this.catalogue().filter(c => c.rule === 'INDICATED'));

  complaint = computed(() => {
    const f = this.form();
    if (!f) return '—';
    if (f.intent === 'LEISURE') return 'Here to relax';
    return f.mainComplaint ? label(f.mainComplaint) : (f.mainComplaintOther || 'Not stated');
  });

  /** Every point scored, so the whole sheet is finished. */
  complete = computed(() =>
    this.findings().length > 0 && this.findings().every(f => f.after !== null));

  async ngOnInit(): Promise<void> {
    const fromQuery = this.route.snapshot.queryParamMap.get('formId');
    await this.load(fromQuery);
  }

  async load(formId: string | null): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const sched = await this.ops.schedule(undefined, this.ctx.branchId())
        .catch(() => [] as ScheduleRow[]);
      this.today.set(sched);
      if (!formId) { void this.loadWeek(); }

      // No id in the URL: show today's assessments and let staff CHOOSE.
      //
      // This used to guess - it opened the next booked session that had an
      // assessment - so tapping "Pre-assessments" in the sidebar dropped you
      // into one named client's medical record without asking. On a screen at a
      // front desk that is the wrong default twice over: it is not the record
      // you meant to open, and it puts somebody's pain map in front of whoever
      // is standing there.
      if (!formId) {
        this.form.set(null);
        return;
      }
      const id = formId;
      const f = await this.api.form(id);
      this.form.set(f);
      this.notes.set(f.remarks ?? '');
      this.catalogue.set(await this.ops.catalogue(id).catch(() => [] as CatalogueEntry[]));
    } catch {
      this.error.set('We could not open that assessment.');
    } finally {
      this.loading.set(false);
    }
  }

  val(ev: Event): string { return (ev.target as HTMLTextAreaElement).value; }

  num(ev: Event): number { return Number((ev.target as HTMLSelectElement).value); }

  scores = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  abs(n: number): number { return Math.abs(n); }

  submitted = computed(() => {
    const iso = this.form()?.createdAt;
    if (!iso) return 'an earlier visit';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? iso
      : d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  });

  /**
   * One point at a time, saved as it is entered. A single "save all" button is
   * how half a sheet gets lost when the front desk phone rings.
   */
  async setAfter(id: string, after: number): Promise<void> {
    if (!id || this.saving()) return;
    this.saving.set(id);
    try {
      const saved: PatientIntakeModel = await this.api.recordAfter(id, after);
      this.form.update(f => !f ? f : {
        ...f,
        painPoints: f.painPoints.map(p =>
          p.id === id ? { ...p, painScoreAfter: saved.painScoreAfter ?? after } : p),
      });
      this.toast.show(`AFTER recorded — appended to the record, nothing overwritten.`);
    } catch {
      this.toast.show('That did not save. The record is unchanged.', 3200);
    } finally {
      this.saving.set(null);
    }
  }
}

/** LOWER_BACK_PAIN -> Lower Back Pain. Enum constants are not words. */
function label(v: string): string {
  return v.split('_').map(w => w.charAt(0) + w.slice(1).toLowerCase()).join(' ');
}

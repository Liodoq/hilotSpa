import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { ActivatedRoute } from '@angular/router';
import { AdminApi, MonthRow, Reports as ReportsDto } from '../../../core/admin.api';
import { AuthService } from '../../../core/auth.service';
import { BranchContext } from '../../../core/branch-context';
import { FormsApi } from '../../../core/forms.api';
import { DateField } from '../../../shared/date-field/date-field';
import { Branch } from '../../../core/models';
import { describeHttpError } from '../../../core/http-error';

/**
 * A3 - most-availed treatment and peak month.
 *
 * Every figure is counted from the appointment table when the page loads, like
 * A1. Nothing is stored or rolled up, so the same number comes back out of psql.
 *
 * The one thing this screen must never do is show a ranking without saying what
 * it counted. "Most availed" and "most booked" are different claims, and people
 * cancel more of some treatments than others, so the server decides which basis
 * it used and this page prints that sentence word for word.
 */
@Component({
  selector: 'app-admin-reports',
  imports: [DashShell, FormsModule, DecimalPipe, DateField],
  templateUrl: './reports.html',
  styleUrl: './reports.scss',
})
export class AdminReports implements OnInit {
  private api = inject(AdminApi);
  private formsApi = inject(FormsApi);
  private auth = inject(AuthService);

  /**
   * Which AREA this page is being shown in, from the route rather than from the
   * signed-in role - an administrator opening the branch console is looking at
   * the staff area and must keep the staff sidebar.
   */
  protected readonly area: 'STAFF' | 'ADMIN' =
    inject(ActivatedRoute).snapshot.data['area'] === 'STAFF' ? 'STAFF' : 'ADMIN';

  private ctx = inject(BranchContext);

  /**
   * Locked to one branch, and therefore not allowed to offer a choice.
   *
   * TWO ways to be locked, and B136 was forgetting the second:
   *
   *  - a STAFF account, whose branch is in their token and cannot be changed;
   *  - an ADMINISTRATOR who has ENTERED a branch. They could ask for all
   *    branches, but they have said which one they are in, and the console
   *    header says BULAN beside this page. A report headed "All branches"
   *    under a badge reading BULAN is the screen contradicting itself - the
   *    same fault as B101, where a client screen showed an administrator
   *    somebody else's booking.
   *
   * Hiding the select rather than disabling it is also deliberate for the
   * staff case: the server IGNORES a branchId from a staff token rather than
   * validating it, so a control that appeared to work and silently did nothing
   * would be worse than none at all.
   */
  protected readonly locked = this.auth.role() !== 'ADMIN' || this.ctx.active();
  protected readonly canPickBranch = !this.locked;

  data = signal<ReportsDto | null>(null);
  branches = signal<Branch[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  /** The controls. Empty string means "not set", which the API reads as its default. */
  from = signal('');
  to = signal('');
  /** Pre-set from the branch an administrator has entered. See `locked`. */
  branchId = signal(this.ctx.branchId() ?? '');

  top = computed(() => this.data()?.services?.[0] ?? null);
  branchRows = computed(() => this.data()?.branches ?? []);
  months = computed(() => this.data()?.months ?? []);

  /**
   * The tallest bar in the month chart.
   *
   * Scaled to the busiest month rather than to a round number, because the
   * question this chart answers is "which month stands out", and a chart scaled
   * to 100 when the peak is 9 answers it by making every month look identical.
   */
  private high = computed(() =>
    Math.max(1, ...this.months().map(m => m.visits)));

  barWidth(m: MonthRow): string {
    return `${Math.round((m.visits / this.high()) * 100)}%`;
  }

  /** A heading that states the scope, so a printout is readable on its own. */
  heading = computed(() => {
    const d = this.data();
    return d ? `Reports — ${d.branchName}` : 'Reports';
  });

  /**
   * The branch table, but only when it is a comparison.
   *
   * The server already sends an empty list for a single-branch scope. This is
   * belt and braces on the ONE screen where showing a table of one row headed
   * "Branches" would restate the contradiction B136 was about.
   */
  showBranchTable = computed(() => !this.locked && this.branchRows().length > 1);

  /**
   * What was actually generated, said above the numbers.
   *
   * Read from the RESPONSE, never from the picker. The picker says what was
   * asked for; after a slow request or a failed one those are different things,
   * and a figure labelled with a scope it does not have is the whole family of
   * bug this page keeps being corrected for.
   */
  scopeLabel = computed(() => this.data()?.branchName ?? '');

  /** True when this report covers every branch, so the label can say so plainly. */
  allBranches = computed(() => this.data()?.branchId == null && !this.locked);

  /**
   * Set the range to one month and regenerate.
   *
   * The month list was a picture; this makes it a control. "Which month was
   * busiest" is almost always followed by "what happened in it", and that was
   * previously two date fields of typing.
   */
  async openMonth(month: string): Promise<void> {
    const [y, m] = month.split('-').map(Number);
    const last = new Date(y, m, 0).getDate();
    this.from.set(`${month}-01`);
    this.to.set(`${month}-${String(last).padStart(2, '0')}`);
    await this.load();
  }

  ngOnInit(): void {
    void this.loadBranches();
    void this.load();
  }

  private async loadBranches(): Promise<void> {
    if (!this.canPickBranch) { return; }
    try {
      this.branches.set(await this.formsApi.branches());
    } catch {
      // A branch filter that failed to populate is a missing convenience, not a
      // reason to deny the administrator the report itself.
      this.branches.set([]);
    }
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.data.set(await this.api.reports({
        from: this.from() || undefined,
        to: this.to() || undefined,
        branchId: this.branchId() || undefined,
      }));
    } catch (e: unknown) {
      this.error.set(describeHttpError(e, 'The report could not be generated.'));
      this.data.set(null);
    } finally {
      this.loading.set(false);
    }
  }

  /** Twelve months back, which is also what the server does with no range. */
  clearRange(): void {
    this.from.set('');
    this.to.set('');
    void this.load();
  }

  /** 1 January of the current year to today. The figure a year-end report wants. */
  thisYear(): void {
    const now = new Date();
    this.from.set(`${now.getFullYear()}-01-01`);
    this.to.set(now.toISOString().slice(0, 10));
    void this.load();
  }

  /**
   * The table as a file, for the appendix.
   *
   * CSV rather than a print stylesheet: the numbers in a thesis appendix have
   * to be re-checkable, and a reader who can open the rows in a spreadsheet can
   * add up the column themselves. The basis line is written into the file, not
   * just shown on screen - a table that travels without its definition is the
   * thing this whole page exists to avoid.
   */
  downloadCsv(): void {
    const d = this.data();
    if (!d) { return; }

    const q = (v: string | number) => `"${String(v).replace(/"/g, '""')}"`;
    const lines: string[] = [
      q('HilotSpa report') + ',' + q(d.branchName),
      q('Range') + ',' + q(`${d.from} to ${d.to} inclusive`),
      q('Generated') + ',' + q(d.generatedAt),
      q('Counted') + ',' + q(d.countedNote),
      '',
      [q('Treatment'), q('Visits'), q('Share %'), q('Revenue PHP')].join(','),
      ...d.services.map(s =>
        [q(`${s.name} (${s.durationMinutes} min)`), s.visits, s.pct, s.revenue].join(',')),
      '',
      [q('Month'), q('Visits'), q('Revenue PHP'), q('Peak')].join(','),
      ...d.months.map(m =>
        [q(m.label), m.visits, m.revenue, q(m.peak ? 'yes' : '')].join(',')),
    ];

    const url = URL.createObjectURL(
      new Blob([lines.join('\r\n')], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `hilotspa-report-${d.from}-to-${d.to}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}

import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { AdminApi, DueRow, NotificationRow } from '../../../core/admin.api';
import { FormsApi } from '../../../core/forms.api';
import { Branch } from '../../../core/models';
import { DateField } from '../../../shared/date-field/date-field';
import { AuthService } from '../../../core/auth.service';
import { BranchContext } from '../../../core/branch-context';
import { NodeStore } from '../../../core/node.store';
import { describeHttpError } from '../../../core/http-error';

/**
 * A8 / S6 - reminders.
 *
 * Two lists, and the distinction between them is the whole design.
 *
 * WHO IS DUE answers "who is booked that day, and which of them still has not
 * heard from us". THE LOG answers "what did we actually send". They are not the
 * same question and neither can be derived from the other: a client nobody has
 * attempted has no log row at all, so in the log they are invisible - in
 * exactly the list you would use to notice them.
 *
 * Sending is per visit, beside the named client. The whole-day run is still
 * there for a morning that was missed, but it refuses repeats by design, so it
 * can never serve the actual case: one person rings to say they never got it.
 */
@Component({
  selector: 'app-admin-notifications',
  imports: [DashShell, FormsModule, DatePipe, DateField],
  templateUrl: './notifications.html',
  styleUrl: './notifications.scss',
})
export class AdminNotifications implements OnInit {
  /**
   * 3.32 - this node sends for the branch it serves and no other, so the page
   * says which before anybody presses anything. The server enforces it either
   * way; being told afterwards is a worse way to find out.
   */
  protected site = inject(NodeStore);

  private api = inject(AdminApi);
  private formsApi = inject(FormsApi);
  private auth = inject(AuthService);
  private ctx = inject(BranchContext);

  /** The area being shown, from the route. See the same field on A7 Reports. */
  protected readonly area: 'STAFF' | 'ADMIN' =
    inject(ActivatedRoute).snapshot.data['area'] === 'STAFF' ? 'STAFF' : 'ADMIN';

  /**
   * The branch this screen is scoped to, or null for every branch.
   *
   * Null only for an administrator who has NOT entered a branch. One who has is
   * scoped like staff are - the console header says BULAN beside this page, and
   * a list showing Sorsogon's clients under that badge is the screen
   * contradicting itself (B136).
   *
   * Staff send nothing: their branch is in the token and the server ignores
   * anything this could put in the URL.
   */
  private readonly forced: string | null =
    this.auth.role() === 'ADMIN' ? this.ctx.branchId() : null;

  /**
   * An administrator outside any branch may choose one here.
   *
   * Separate from `forced`: entering a branch from the console PINS this page,
   * and offering a select that could contradict the badge beside it would put
   * back exactly the contradiction B136 was about. So the picker appears only
   * when nothing has already decided.
   */
  protected readonly canPickBranch = this.auth.role() === 'ADMIN' && this.forced === null;
  branches = signal<Branch[]>([]);
  pickedBranch = signal('');

  /** What every request on this page is scoped to. */
  private scope(): string | null {
    return this.forced ?? (this.pickedBranch() || null);
  }

  /** What the reader is looking at, said in words above the lists. */
  scopeLabel = computed(() => {
    if (this.forced) { return this.ctx.name() || 'This branch'; }
    if (!this.canPickBranch) { return 'Your branch'; }
    const id = this.pickedBranch();
    if (!id) { return 'All branches'; }
    const b = this.branches().find(x => x.id === id);
    return b?.name || b?.branchName || 'One branch';
  });

  rows = signal<NotificationRow[]>([]);
  due = signal<DueRow[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  running = signal(false);
  sendingOne = signal<string | null>(null);
  runNote = signal<string | null>(null);

  /** The day the VISITS fall on, not the day the mail goes out. */
  day = signal('');

  sent = computed(() => this.rows().filter(r => r.status === 'SENT').length);
  failed = computed(() => this.rows().filter(r => r.status === 'FAILED').length);
  skipped = computed(() => this.rows().filter(r => r.status === 'SKIPPED').length);

  /**
   * Rows claimed but never answered for.
   *
   * A SENDING row that is not from the last few seconds means a send that died
   * mid-flight, and it is the only state here that indicates a fault in this
   * system rather than in the mail server or the client's record.
   */
  stuck = computed(() => this.rows().filter(r => r.status === 'SENDING').length);

  /** Booked that day and never yet written to. The number that matters. */
  untold = computed(() => this.due().filter(d => !d.status).length);

  ngOnInit(): void {
    this.site.ensure();
    void this.loadBranches();
    void this.load();
    void this.loadDue();
  }

  private async loadBranches(): Promise<void> {
    if (!this.canPickBranch) { return; }
    try {
      this.branches.set(await this.formsApi.branches());
    } catch {
      // A picker that failed to populate is a missing convenience, not a reason
      // to deny the administrator the lists themselves.
      this.branches.set([]);
    }
  }

  /** Changing branch reloads both lists — they are two views of one scope. */
  async pickBranch(v: string): Promise<void> {
    this.pickedBranch.set(v);
    await Promise.all([this.load(), this.loadDue()]);
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.rows.set(await this.api.notifications(this.scope()));
    } catch (e: unknown) {
      this.error.set(describeHttpError(e, 'The notification log could not be read.'));
    } finally {
      this.loading.set(false);
    }
  }

  async loadDue(): Promise<void> {
    try {
      this.due.set(await this.api.dueReminders(this.day() || undefined, this.scope()));
    } catch {
      // The due list is the more useful half, but it is not worth denying the
      // administrator the log because this one query failed.
      this.due.set([]);
    }
  }

  /** Changing the date reloads who is due, without touching the log. */
  async pickDay(v: string): Promise<void> {
    this.day.set(v);
    await this.loadDue();
  }

  /**
   * Send ONE client's reminder.
   *
   * Safe to press on somebody already reminded - that is the case it exists
   * for. The server records the attempt count and who pressed it, so a re-send
   * is visible afterwards rather than being indistinguishable from the first.
   */
  async sendOne(d: DueRow): Promise<void> {
    if (this.sendingOne() || this.running()) { return; }
    this.sendingOne.set(d.appointmentId);
    this.runNote.set(null);
    try {
      const r = await this.api.sendOneReminder(d.appointmentId, this.scope());
      this.runNote.set(`${d.client} — ${r.note}`);
      await Promise.all([this.load(), this.loadDue()]);
    } catch (e: unknown) {
      this.runNote.set(describeHttpError(e, 'That reminder could not be sent.'));
    } finally {
      this.sendingOne.set(null);
    }
  }

  /**
   * The whole day at once.
   *
   * Kept for a morning the scheduled job missed. It will not repeat anybody,
   * which is why it cannot serve the one-client case and why the per-row button
   * above exists.
   */
  async run(): Promise<void> {
    if (this.running()) { return; }
    this.running.set(true);
    this.runNote.set(null);
    try {
      const r = await this.api.runReminders(this.day() || undefined, this.scope());
      this.runNote.set(`${r.visitDay}: ${r.note}`);
      await Promise.all([this.load(), this.loadDue()]);
    } catch (e: unknown) {
      this.runNote.set(describeHttpError(e, 'The reminder run could not be started.'));
    } finally {
      this.running.set(false);
    }
  }

  tone(status: string | null): string {
    switch (status) {
      case 'SENT':    return 'ok';
      case 'FAILED':  return 'bad';
      case 'SKIPPED': return 'warn';
      case 'SENDING': return 'mute';
      default:        return 'mute';
    }
  }

  /** What a person reads. Never the enum. */
  label(status: string | null): string {
    switch (status) {
      case 'SENT':    return 'Sent';
      case 'FAILED':  return 'Failed';
      case 'SKIPPED': return 'Not sent';
      case 'SENDING': return 'In flight';
      default:        return 'Not told yet';
    }
  }
}

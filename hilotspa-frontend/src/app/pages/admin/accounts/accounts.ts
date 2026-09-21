import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { ToastService } from '../../../core/toast.service';
import { AccountRow, OpsApi } from '../../../core/ops.api';
import { describeHttpError } from '../../../core/http-error';
import { FormsApi } from '../../../core/forms.api';
import { Branch } from '../../../core/models';

/**
 * A3 — the only place a role is ever assigned.
 *
 * Self-registration hardcodes CUSTOMER and RegisterRequest has no role field at
 * all, so there is nothing for a client to abuse. Promoting an account is an
 * administrator action and lands in the audit log like any other write.
 */
@Component({
  selector: 'app-admin-accounts',
  imports: [DashShell],
  templateUrl: './accounts.html',
  styleUrl: './accounts.scss',
})
export class AdminAccounts implements OnInit {
  private api = inject(OpsApi);
  private formsApi = inject(FormsApi);
  protected toast = inject(ToastService);
  protected roles = ['All roles', 'STAFF', 'ADMIN', 'CUSTOMER'];
  /** What this form may assign. Same three the server accepts. */
  protected assignable: Array<'CUSTOMER' | 'STAFF' | 'ADMIN'> = ['CUSTOMER', 'STAFF', 'ADMIN'];

  rows = signal<AccountRow[]>([]);
  branches = signal<Branch[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  saving = signal<string | null>(null);

  q = signal('');
  role = signal(this.roles[0]);

  /**
   * Add / edit. This is the ONLY place a role is assigned anywhere in the system.
   *
   * Self-registration hardcodes CUSTOMER and RegisterRequest has no role field,
   * which is a property the tests defend — but it also means that without this
   * form a staff account could only ever come from the seeder.
   */
  drawer = signal<AccountForm | null>(null);

  /**
   * 3.36 - the front desk helping somebody who is locked out.
   *
   * Two shapes of the same person walk in. One is standing at the counter with
   * their phone at home, and needs a password read out to them now. The other
   * is on the telephone, and should get the same link the public flow sends so
   * that nobody at the spa ever handles their password.
   *
   * `tempPassword` holds a temporary password for exactly as long as the drawer is
   * open. It is never written to a signal that outlives the drawer, never put
   * in the row list, and never re-fetchable - the server keeps only a BCrypt
   * hash. Closing the drawer is the same as losing it.
   */
  resetting = signal(false);
  resetMode = signal<'TEMPORARY' | 'EMAIL'>('TEMPORARY');
  resetCustom = signal('');
  tempPassword = signal<string | null>(null);
  resetNote = signal('');

  /** The server enforces all of this too; the form only saves a round trip. */
  canSave = computed(() => {
    const d = this.drawer();
    if (!d) return false;
    if (!d.firstName.trim() || !d.email.trim()) return false;
    if (d.role === 'STAFF' && !d.branchId) return false;
    if (!d.id && d.password.trim().length < 8) return false;
    return true;
  });

  shown = computed(() => {
    const term = this.q().trim().toLowerCase();
    const r = this.role();
    return this.rows()
      .filter(a => (r === 'All roles' || a.role === r) &&
        (term === '' || (this.name(a) + ' ' + a.email).toLowerCase().includes(term)))
      .sort((a, b) => this.name(a).localeCompare(this.name(b)));
  });

  counts = computed(() => ({
    total: this.rows().length,
    staff: this.rows().filter(a => a.role === 'STAFF').length,
    admin: this.rows().filter(a => a.role === 'ADMIN').length,
    disabled: this.rows().filter(a => !a.enabled).length,
  }));

  /** A STAFF account with no branch can see nothing at all — worth flagging. */
  orphanStaff = computed(() =>
    this.rows().filter(a => a.role === 'STAFF' && !a.branchId).length);

  async ngOnInit(): Promise<void> { await this.load(); }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const [users, branches] = await Promise.all([
        this.api.accounts(),
        this.formsApi.branches().catch(() => [] as Branch[]),
      ]);
      this.rows.set(users);
      this.branches.set(branches);
    } catch {
      this.error.set('We could not load accounts.');
    } finally {
      this.loading.set(false);
    }
  }

  // --------------------------------------------------------------- drawer

  add(): void {
    this.clearReset();
    this.drawer.set({ id: null, firstName: '', lastName: '', email: '',
      role: 'STAFF', branchId: null, password: '' });
  }

  edit(a: AccountRow): void {
    this.clearReset();
    this.drawer.set({ id: a.id, firstName: a.firstName ?? '', lastName: a.lastName ?? '',
      email: a.email ?? '', role: a.role, branchId: a.branchId, password: '' });
  }

  patch(part: Partial<AccountForm>): void {
    this.drawer.update(d => {
      if (!d) return d;
      const next = { ...d, ...part };
      // A role that is not STAFF cannot carry a branch: branch scoping reads it
      // from the token, and a customer with a branch would mean nothing.
      if (next.role !== 'STAFF') next.branchId = null;
      return next;
    });
  }

  close(): void { this.clearReset(); this.drawer.set(null); }

  /** Nothing about a reset survives the drawer. */
  private clearReset(): void {
    this.resetting.set(false);
    this.resetMode.set('TEMPORARY');
    this.resetCustom.set('');
    this.tempPassword.set(null);
    this.resetNote.set('');
  }

  /**
   * Do the reset.
   *
   * Note what is NOT here: a confirmation dialog. The action is recoverable -
   * doing it twice just makes a second temporary password - and a dialog in
   * front of the front desk while a client waits is friction spent on nothing.
   * What IS here is the audit line the server writes, which is the thing that
   * actually answers "who changed this account".
   */
  async resetPassword(): Promise<void> {
    const d = this.drawer();
    if (!d?.id || this.resetting()) return;
    const mode = this.resetMode();
    const custom = this.resetCustom().trim();
    if (mode === 'TEMPORARY' && custom.length > 0 && custom.length < 8) {
      this.toast.show('A temporary password must be at least 8 characters.', 3200);
      return;
    }
    this.resetting.set(true);
    this.tempPassword.set(null);
    this.resetNote.set('');
    try {
      const res = await this.api.resetAccountPassword(d.id, {
        mode,
        temporaryPassword: mode === 'TEMPORARY' && custom ? custom : undefined,
      });
      this.tempPassword.set(res.temporaryPassword);
      this.resetNote.set(res.message);
      this.resetCustom.set('');
      if (mode === 'EMAIL') this.toast.show(res.message, 4200);
    } catch (e: unknown) {
      this.toast.show(describeHttpError(e, 'That reset did not go through. Nothing was changed.'), 4200);
    } finally {
      this.resetting.set(false);
    }
  }

  async copyTempPassword(): Promise<void> {
    const pw = this.tempPassword();
    if (!pw) return;
    try {
      await navigator.clipboard.writeText(pw);
      this.toast.show('Copied. It is still only in this window — it is not stored anywhere.');
    } catch {
      this.toast.show('Your browser would not let us copy. Read it from the screen.', 3200);
    }
  }

  async save(): Promise<void> {
    const d = this.drawer();
    if (!d || !this.canSave() || this.saving()) return;
    this.saving.set(d.id ?? 'new');
    try {
      const body = {
        firstName: d.firstName.trim(), lastName: d.lastName.trim(), email: d.email.trim(),
        role: d.role, branchId: d.role === 'STAFF' ? d.branchId : null,
      };
      if (d.id) {
        await this.api.saveAccount(d.id, body);
        this.toast.show(`${body.firstName} ${body.lastName} updated — now ${d.role}.`);
      } else {
        await this.api.createAccount({ ...body, password: d.password });
        this.toast.show(`${body.firstName} ${body.lastName} created as ${d.role}.`);
      }
      this.drawer.set(null);
      await this.load();
    } catch (e: unknown) {
      this.toast.show(describeHttpError(e, 'That did not save. Nothing was changed.'), 3600);
    } finally {
      this.saving.set(null);
    }
  }

  name(a: AccountRow): string {
    return [a.firstName, a.lastName].filter(Boolean).join(' ') || a.email;
  }

  branchName(id: string | null): string {
    if (!id) return '—';
    const b = this.branches().find(x => x.id === id);
    return b?.name ?? b?.branchName ?? id.slice(0, 8);
  }

  async toggleEnabled(a: AccountRow): Promise<void> {
    if (this.saving()) return;
    const next = !a.enabled;
    this.patchRow(a.id, { enabled: next });
    this.saving.set(a.id);
    try {
      const saved = await this.api.saveAccount(a.id, { ...a, enabled: next });
      this.patchRow(a.id, saved);
      this.toast.show(`${this.name(a)} ${next ? 'enabled' : 'disabled'}. History is kept either way.`);
    } catch {
      this.patchRow(a.id, { enabled: !next });
      this.toast.show('That did not save. Nothing was changed.', 3200);
    } finally {
      this.saving.set(null);
    }
  }

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }

  /** Optimistic row update in the list — not the drawer's form patch. */
  private patchRow(id: string, p: Partial<AccountRow>): void {
    this.rows.update(list => list.map(a => a.id === id ? { ...a, ...p } : a));
  }
}

/** What the drawer is editing. `password` is only used when creating. */
interface AccountForm {
  id: string | null;
  firstName: string;
  lastName: string;
  email: string;
  role: 'CUSTOMER' | 'STAFF' | 'ADMIN';
  branchId: string | null;
  password: string;
}

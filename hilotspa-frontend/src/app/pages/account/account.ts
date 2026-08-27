import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { Toast } from '../../shared/toast/toast';
import { ToastService } from '../../core/toast.service';
import { FormsApi } from '../../core/forms.api';
import { AuthService } from '../../core/auth.service';
import { AccountMe } from '../../core/models';
import { describeHttpError } from '../../core/http-error';

/**
 * Account details — name, email, contact, address, password.
 *
 * Separate from /profile, which holds DEMOGRAPHICS (age, sex, civil status,
 * occupation, height, weight). They are different things with different
 * lifetimes: your surname is not clinical data, and your weight is not part of
 * your login. Keeping them apart also keeps the endpoints apart, so an edit
 * here can never touch a clinical record.
 */
@Component({
  selector: 'app-account',
  imports: [AppNav, Toast],
  templateUrl: './account.html',
  styleUrl: './account.scss',
})
export class Account {
  private api = inject(FormsApi);
  private auth = inject(AuthService);
  private router = inject(Router);
  protected toast = inject(ToastService);

  protected me = signal<AccountMe | null>(null);
  protected loading = signal(true);
  protected saving = signal(false);
  protected error = signal('');

  // password change is its own form on purpose — it needs the current password
  protected current = signal('');
  protected next = signal('');
  protected confirm = signal('');
  protected pwError = signal('');
  protected pwSaving = signal(false);

  constructor() { void this.load(); }

  private async load(): Promise<void> {
    try {
      this.me.set(await this.api.me());
    } catch (e: unknown) {
      this.error.set(describeHttpError(e, 'We could not load your account.'));
    } finally {
      this.loading.set(false);
    }
  }

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }

  patch(part: Partial<AccountMe>): void {
    const m = this.me();
    if (m) this.me.set({ ...m, ...part });
  }

  async save(): Promise<void> {
    const m = this.me();
    if (!m || this.saving()) return;
    this.saving.set(true);
    this.error.set('');
    try {
      this.me.set(await this.api.updateMe({
        firstName: m.firstName, middleName: m.middleName, lastName: m.lastName,
        contact: m.contact, address: m.address, email: m.email,
      }));
      this.toast.show('Your details were saved');
    } catch (e: unknown) {
      // 409 means the email belongs to somebody else. Saying so beats a
      // generic failure the client cannot act on.
      this.error.set(describeHttpError(e, 'We could not save your details.'));
    } finally {
      this.saving.set(false);
    }
  }

  async changePassword(): Promise<void> {
    if (this.pwSaving()) return;
    this.pwError.set('');
    if (this.next() !== this.confirm()) {
      this.pwError.set('The two new passwords do not match.'); return;
    }
    if (this.next().length < 8) {
      this.pwError.set('Your new password must be at least 8 characters.'); return;
    }
    this.pwSaving.set(true);
    try {
      await this.api.changeMyPassword(this.current(), this.next());
      this.current.set(''); this.next.set(''); this.confirm.set('');
      this.toast.show('Password changed');
    } catch (e: unknown) {
      this.pwError.set(describeHttpError(e, 'We could not change your password.'));
    } finally {
      this.pwSaving.set(false);
    }
  }

  demographics(): void { this.router.navigateByUrl('/profile'); }
  signOut(): void { this.auth.logout(); }
}

import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { Toast } from '../../shared/toast/toast';
import { ToastService } from '../../core/toast.service';
import { FormsApi } from '../../core/forms.api';
import { AuthService } from '../../core/auth.service';
import { ProfileStore } from '../../core/profile.store';
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
 *
 * What changed in the redesign is not that split — it is that the client was
 * being SHOWN the split. The page used to end with a grey card explaining that
 * "health information lives separately", which is a sentence about our database
 * schema. It now simply shows those details, read-only, with a way through to
 * the page that owns them. The separation is still real; it is no longer the
 * client's problem.
 */
@Component({
  selector: 'app-account',
  imports: [AppNav, Toast, RouterLink],
  templateUrl: './account.html',
  styleUrl: './account.scss',
})
export class Account {
  private api = inject(FormsApi);
  private auth = inject(AuthService);
  private router = inject(Router);
  protected toast = inject(ToastService);
  protected profile = inject(ProfileStore);

  protected me = signal<AccountMe | null>(null);
  protected loading = signal(true);
  protected saving = signal(false);
  protected error = signal('');

  /**
   * Read-only until Edit is pressed — the same rule /profile has always had.
   * Six permanently-live inputs on a page you mostly visit to LOOK at your own
   * address is an invitation to change something by leaning on the keyboard.
   */
  protected editing = signal(false);
  private snapshot: AccountMe | null = null;

  /** The password form is three empty boxes on a page that is otherwise a list
   *  of facts. It stays folded away until somebody wants it. */
  protected showPw = signal(false);

  // password change is its own form on purpose — it needs the current password
  protected current = signal('');
  protected next = signal('');
  protected confirm = signal('');
  protected pwError = signal('');
  protected pwSaving = signal(false);

  constructor() {
    void this.load();
    // Cheap, cached, and fails quietly: the details summary below should not be
    // stale just because the client last edited it on another device.
    void this.profile.load();
  }

  private async load(): Promise<void> {
    try {
      this.me.set(await this.api.me());
    } catch (e: unknown) {
      this.error.set(describeHttpError(e, 'We could not load your account.'));
    } finally {
      this.loading.set(false);
    }
  }

  /** The page title. Their name, not the word "Account". */
  protected fullName = computed(() => {
    const m = this.me();
    if (!m) return '';
    const parts = [m.firstName, m.middleName, m.lastName]
      .map(s => (s ?? '').trim()).filter(Boolean);
    return parts.join(' ');
  });

  protected p = this.profile.profile;

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }

  patch(part: Partial<AccountMe>): void {
    const m = this.me();
    if (m) this.me.set({ ...m, ...part });
  }

  startEdit(): void {
    const m = this.me();
    if (!m) return;
    this.snapshot = { ...m };
    this.error.set('');
    this.editing.set(true);
  }

  /** Discard, do not persist. `me` is edited in place as you type, so backing
   *  out has to put the loaded copy back. */
  cancelEdit(): void {
    if (this.snapshot) this.me.set({ ...this.snapshot });
    this.error.set('');
    this.editing.set(false);
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
      this.editing.set(false);
      this.toast.show('Your details were saved');
    } catch (e: unknown) {
      // 409 means the email belongs to somebody else. Saying so beats a
      // generic failure the client cannot act on. Stay in edit mode — the
      // typed value is the only copy and closing the editor would lose it.
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
      this.showPw.set(false);
      this.toast.show('Password changed');
    } catch (e: unknown) {
      this.pwError.set(describeHttpError(e, 'We could not change your password.'));
    } finally {
      this.pwSaving.set(false);
    }
  }

  closePw(): void {
    this.current.set(''); this.next.set(''); this.confirm.set('');
    this.pwError.set('');
    this.showPw.set(false);
  }

  demographics(): void { this.router.navigateByUrl('/profile'); }
  signOut(): void { this.auth.logout(); }
}

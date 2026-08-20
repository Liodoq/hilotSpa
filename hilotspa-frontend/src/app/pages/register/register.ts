import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Logo } from '../../shared/logo/logo';
import { describeHttpError } from '../../core/http-error';

@Component({
  selector: 'app-register',
  imports: [RouterLink, Logo],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register {
  private auth = inject(AuthService);
  private router = inject(Router);

  firstName = signal('');
  middleName = signal('');
  lastName = signal('');
  contact = signal('');
  address = signal('');
  email = signal('');
  password = signal('');
  confirm = signal('');
  busy = signal(false);
  error = signal('');

  // There is deliberately NO role field here. RegisterRequest on the server has
  // none either, and the service hardcodes CUSTOMER — this endpoint is
  // unauthenticated, so anything it accepts is attacker-controlled.
  canSubmit = computed(() =>
    this.firstName().trim() !== '' &&
    this.lastName().trim() !== '' &&
    this.contact().trim() !== '' &&
    this.address().trim() !== '' &&
    this.email().trim() !== '' &&
    this.password().length >= 8 &&
    this.password() === this.confirm());

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }

  async submit(): Promise<void> {
    if (this.busy()) return;
    if (this.password() !== this.confirm()) {
      this.error.set('The two passwords do not match.');
      return;
    }
    if (this.password().length < 8) {
      this.error.set('Please use at least 8 characters for your password.');
      return;
    }
    this.busy.set(true);
    this.error.set('');
    try {
      await this.auth.register({
        firstName: this.firstName().trim(),
        lastName: this.lastName().trim(),
        middleName: this.middleName().trim() || undefined,
        contact: this.contact().trim(),
        address: this.address().trim(),
        email: this.email().trim(),
        password: this.password(),
      });
      await this.router.navigateByUrl('/assessment/intent');
    } catch (e: unknown) {
      console.error('[register] request failed', e);
      this.error.set(describeHttpError(e, 'We could not create your account.'));
    } finally {
      this.busy.set(false);
    }
  }
}

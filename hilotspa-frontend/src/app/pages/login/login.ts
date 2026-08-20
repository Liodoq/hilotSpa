import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Logo } from '../../shared/logo/logo';
import { describeHttpError } from '../../core/http-error';

@Component({
  selector: 'app-login',
  imports: [RouterLink, Logo],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  email = signal('');
  password = signal('');
  busy = signal(false);
  error = signal('');

  canSubmit = computed(() => this.email().trim().length > 0 && this.password().length > 0);

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }

  /** Each role starts where its work is. Figures 3.1-3.3 are three different jobs. */
  private landing(): string {
    switch (this.auth.role()) {
      case 'ADMIN': return '/admin/overview';
      case 'STAFF': return '/staff/dashboard';
      default:      return '/home';
    }
  }

  async submit(): Promise<void> {
    if (!this.canSubmit() || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await this.auth.login({ email: this.email().trim(), password: this.password() });
      const next = this.route.snapshot.queryParamMap.get('next');
      await this.router.navigateByUrl(next ?? this.landing());
    } catch (e: unknown) {
      // The backend deliberately returns the same 401 for an unknown email and
      // a wrong password, so an attacker cannot enumerate accounts. Do not
      // "improve" this by saying which one was wrong.
      console.error('[login] request failed', e);
      this.error.set(describeHttpError(e, 'We could not log you in.'));
    } finally {
      this.busy.set(false);
    }
  }
}

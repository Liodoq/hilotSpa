import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Logo } from '../../shared/logo/logo';
import { NodeStore } from '../../core/node.store';

/**
 * Step one of a forgotten password.
 *
 * The screen has exactly one outcome. Whatever the server finds, it answers
 * with the same sentence, and this page shows that sentence - it does not say
 * "sent" or "no such account", because the endpoint is open to anyone and a
 * page that distinguished the two would let a stranger test whether a named
 * person is a client here.
 *
 * So the submitted state is terminal on purpose: no second Send button to
 * hammer, and a way back to the login screen rather than a loop.
 */
@Component({
  selector: 'app-forgot-password',
  imports: [RouterLink, Logo],
  templateUrl: './forgot-password.html',
  styleUrl: './forgot-password.scss',
})
export class ForgotPassword {
  protected site = inject(NodeStore);
  private auth = inject(AuthService);

  constructor() { this.site.ensure(); }

  email = signal('');
  busy = signal(false);
  sent = signal('');
  error = signal('');

  canSubmit = computed(() => this.email().trim().includes('@') && !this.busy());

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }

  async submit(): Promise<void> {
    if (!this.canSubmit()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      this.sent.set(await this.auth.forgotPassword(this.email().trim()));
    } catch {
      // A network failure is the only thing that can land here - the endpoint
      // returns 200 for every address it is given. Saying so plainly is better
      // than a message about the email, which would imply we looked.
      this.error.set('We could not reach the spa’s system just now. Try again in a moment.');
    } finally {
      this.busy.set(false);
    }
  }
}

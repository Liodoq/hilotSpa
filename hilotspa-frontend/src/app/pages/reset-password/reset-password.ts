import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Logo } from '../../shared/logo/logo';
import { NodeStore } from '../../core/node.store';
import { describeHttpError } from '../../core/http-error';

/**
 * Step two: the link out of the email.
 *
 * The token is read once, in the constructor, and kept in a signal rather than
 * re-read from the URL on submit. The address bar is the one place a reset
 * token is visible over someone's shoulder, and the browser keeps it in
 * history; holding it in memory means the page can clear the query string
 * without losing the ability to finish.
 *
 * On success it does NOT sign them in - see AuthService.resetPassword.
 */
@Component({
  selector: 'app-reset-password',
  imports: [RouterLink, Logo],
  templateUrl: './reset-password.html',
  styleUrl: './reset-password.scss',
})
export class ResetPassword {
  protected site = inject(NodeStore);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);

  token = signal('');
  password = signal('');
  confirm = signal('');
  busy = signal(false);
  done = signal(false);
  error = signal('');

  constructor() {
    this.site.ensure();
    this.token.set(this.route.snapshot.queryParamMap.get('token') ?? '');
    if (this.token()) {
      // Take it out of the address bar now that we hold it, so it is not left
      // in browser history or read over a shoulder.
      //
      // history.replaceState, NOT router.navigate. Navigating would be a real
      // Angular navigation to the same route with different query params, and
      // anything that re-created this component would re-read a token that is
      // no longer in the URL - the page would then tell somebody holding a
      // perfectly good link that it was incomplete. This edits the address bar
      // and nothing else.
      try {
        history.replaceState(history.state, '', '/reset-password');
      } catch { /* not worth failing the page over */ }
    }
  }

  /** Same rule the server enforces. Shown live so nobody types twice. */
  longEnough = computed(() => this.password().length >= 8);
  matches = computed(() => this.confirm().length > 0 && this.confirm() === this.password());

  canSubmit = computed(() =>
    this.token().length > 0 && this.longEnough() && this.matches() && !this.busy());

  val(ev: Event): string { return (ev.target as HTMLInputElement).value; }

  async submit(): Promise<void> {
    if (!this.canSubmit()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      await this.auth.resetPassword(this.token(), this.password());
      this.done.set(true);
      // The token is spent. Drop it so a refresh cannot replay it.
      this.token.set('');
      this.password.set('');
      this.confirm.set('');
    } catch (e: unknown) {
      this.error.set(describeHttpError(e, 'We could not change your password.'));
    } finally {
      this.busy.set(false);
    }
  }
}

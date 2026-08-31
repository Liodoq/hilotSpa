import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { AuthService } from '../../core/auth.service';
import { PublicApi, PublicSpa } from '../../core/public.api';
import { priceLabel } from '../../core/catalogue.store';

/**
 * The front door.
 *
 * Until now every route was behind a guard and `/` redirected to the login
 * form, so the first thing anyone met — a client, an adviser, a panelist
 * opening the URL — was a password box. A spa that cannot show its own menu
 * without an account is not a spa website.
 *
 * Everything here comes from GET /api/v1/public/spa, which is the only
 * unauthenticated read in the system: the spa's own details and its live menu.
 * No protocol rules, no availability, no counts. The menu is the SAME table the
 * assistant works from, so this page cannot drift from what the spa actually
 * sells the way a hand-written marketing page would.
 */
@Component({
  selector: 'app-landing',
  imports: [AppNav, RouterLink],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing implements OnInit {
  private api = inject(PublicApi);
  private router = inject(Router);
  protected auth = inject(AuthService);

  protected priceLabel = priceLabel;

  protected spa = signal<PublicSpa | null>(null);
  protected loading = signal(true);
  protected failed = signal(false);

  /** Six on the front page. The rest are one tap away on /services — a wall of
   *  ten cards is a price list, not an invitation. */
  protected featured = computed(() => (this.spa()?.services ?? []).slice(0, 6));
  protected team = computed(() => this.spa()?.therapists ?? []);

  /** The first letter, for the initial disc. Names here are first names only. */
  initial(name: string): string { return (name || '?').charAt(0).toUpperCase(); }
  protected more = computed(() => Math.max(0, (this.spa()?.services ?? []).length - 6));

  async ngOnInit(): Promise<void> {
    try {
      this.spa.set(await this.api.spa());
    } catch {
      // The spa is real whether or not the server answers. Say what we know and
      // let people reach the branch by phone rather than showing an error page.
      this.failed.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  /** One button, two destinations: straight in for a client who is already
   *  signed in, the sign-up for everyone else. */
  start(): void {
    this.router.navigateByUrl(this.auth.token() ? '/assessment/intent' : '/register');
  }

  photo(file: string | null): string | null {
    return file ? `url(/services/${file})` : null;
  }
}

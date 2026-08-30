import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Connectivity } from '../../core/connectivity';
import { Logo } from '../logo/logo';

/** The customer-facing top bar. Sits above C1, C8, C9, C10. */
@Component({
  selector: 'app-nav',
  imports: [RouterLink, RouterLinkActive, Logo],
  templateUrl: './app-nav.html',
  styleUrl: './app-nav.scss',
})
export class AppNav {
  protected auth = inject(AuthService);
  /** 3.7 - the client's own connection, stated plainly rather than left to be
   *  inferred from a screen that has quietly stopped updating. */
  protected net = inject(Connectivity);
  private router = inject(Router);
  open = signal(false);

  go(path: string): void { this.open.set(false); this.router.navigateByUrl(path); }
  signOut(): void { this.open.set(false); this.auth.logout(); }

  initials = computed(() =>
    (this.auth.fullName() || 'G ?').split(' ').filter(Boolean)
      .slice(0, 2).map(p => p[0]).join('').toUpperCase());
}

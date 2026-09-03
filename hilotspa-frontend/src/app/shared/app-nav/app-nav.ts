import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Connectivity } from '../../core/connectivity';
import { Logo } from '../logo/logo';
import { homeFor, isConsoleRole } from '../../core/role-home';

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

  /** Somebody who WORKS here rather than books here. They see this bar only on
   *  the public pages, so its whole job is to point them back. */
  protected isConsole = computed(() => isConsoleRole(this.auth.role()));

  /** Where the brand mark and "back to your console" both go. */
  protected home = computed(() => this.auth.token() ? homeFor(this.auth.role()) : '/');

  protected consoleLabel = computed(() =>
    this.auth.role() === 'ADMIN' ? 'Administrator console' : 'Branch dashboard');

  initials = computed(() =>
    (this.auth.fullName() || 'G ?').split(' ').filter(Boolean)
      .slice(0, 2).map(p => p[0]).join('').toUpperCase());
}

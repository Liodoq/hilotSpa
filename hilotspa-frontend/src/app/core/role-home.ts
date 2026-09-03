import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';
import { Role } from './models';

/**
 * Where each role's work is — ONE definition, used by the login redirect, the
 * public landing guard and the top bar.
 *
 * It was written three times before: login.ts had a private landing(), the nav
 * hardcoded /home, and the root route had nothing at all. So an administrator
 * signing in could land on the client landing page, be offered MY BOOKINGS and
 * "My health details" in the top bar, and open /booking — which is where B101
 * became visible. Figures 3.1–3.3 are three different jobs; the app now knows
 * that in one place.
 */
export function homeFor(role: Role | null): string {
  switch (role) {
    case 'ADMIN': return '/admin/overview';
    case 'STAFF': return '/staff/dashboard';
    default:      return '/home';
  }
}

/** True when this person works here rather than books here. */
export function isConsoleRole(role: Role | null): boolean {
  return role === 'ADMIN' || role === 'STAFF';
}

/**
 * Paths that only make sense for somebody who is a CLIENT of the spa.
 *
 * Used to sanity-check a ?next= before honouring it: an administrator who hits
 * authGuard on /booking and then signs in should land in their console, not on
 * a client screen that will be empty by definition — mine() returns the
 * caller's own appointments, and an administrator has none.
 */
const CLIENT_ONLY = ['/home', '/book', '/booking', '/profile', '/assessment', '/report'];

export function isClientPath(url: string): boolean {
  const path = url.split('?')[0];
  return CLIENT_ONLY.some(p => path === p || path.startsWith(p + '/'));
}

/**
 * The public landing page is deliberately open — a spa that cannot show its own
 * menu without an account is an intranet. A CUSTOMER may browse it signed in.
 *
 * Staff and administrators may not: for them it is a screen belonging to
 * somebody else's job, with a top bar offering things they do not have.
 */
export const publicHomeGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return isConsoleRole(auth.role())
    ? router.createUrlTree([homeFor(auth.role())])
    : true;
};

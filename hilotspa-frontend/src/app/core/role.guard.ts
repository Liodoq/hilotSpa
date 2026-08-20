import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';
import { Role } from './models';

/**
 * Client-side routing convenience only.
 *
 * The real wall is Spring Security — SecurityConfig already restricts
 * /api/v1/users to ADMIN and scopes forms by branch. A guard stops a wasted
 * navigation; it does not protect data, and it must never be the only check.
 */
export function roleGuard(...allowed: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isLoggedIn()) return router.createUrlTree(['/login']);
    const role = auth.role();
    return role && allowed.includes(role) ? true : router.createUrlTree(['/not-found']);
  };
}

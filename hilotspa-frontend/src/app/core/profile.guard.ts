import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { ProfileStore } from './profile.store';

/**
 * The assessment needs demographics to exist — it does not need to ask for them.
 *
 * If the profile is already filled in, this guard is invisible: the client goes
 * straight from "why are you here" to the body map. If it is not, they are sent
 * to fill it in once and returned to exactly where they were going.
 */
export const profileGuard: CanActivateFn = (_route, state) => {
  const profile = inject(ProfileStore);
  const router = inject(Router);
  if (profile.isComplete()) return true;
  return router.createUrlTree(['/profile'], { queryParams: { next: state.url } });
};

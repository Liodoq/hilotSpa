import { HttpErrorResponse } from '@angular/common/http';
import { API_BASE } from './api.config';

/**
 * Turns an HttpErrorResponse into something a person can act on.
 *
 * The important case is status 0. The browser reports 0 when the request never
 * produced an HTTP response at all — the server is down, the port is wrong, or
 * the CORS preflight was rejected. Lumping that in with "something went wrong"
 * hides the only three things worth checking.
 */
export function describeHttpError(e: unknown, fallback = 'Something went wrong.'): string {
  if (!(e instanceof HttpErrorResponse)) {
    return e instanceof Error ? e.message : fallback;
  }

  if (e.status === 0) {
    return `Cannot reach the server at ${API_BASE}. Check that the backend is running, ` +
           `and that its FRONTEND_ORIGIN matches this page's address.`;
  }

  const serverSaid = typeof e.error === 'string'
    ? e.error
    : (e.error?.message ?? e.error?.error ?? '');

  switch (e.status) {
    case 400: return serverSaid || 'The server rejected that request as invalid.';
    // Not always a login. Registering, or a request made with an expired
    // token, also returns 401 - and 'Invalid email or password' on the sign-up
    // form sends people hunting for a typo that is not there.
    case 401: return serverSaid || 'Invalid email or password.';
    case 403: return 'You are signed in, but not allowed to do that.';
    case 404: return 'Not found.';
    case 409: return serverSaid || 'That already exists.';
    case 500: return 'The server hit an error. Check the backend console.';
    default:  return `${fallback} (HTTP ${e.status}${serverSaid ? ': ' + serverSaid : ''})`;
  }
}

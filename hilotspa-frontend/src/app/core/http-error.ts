import { HttpErrorResponse } from '@angular/common/http';
import { API_BASE } from './api.config';

/**
 * What to say when the server refused and would not say why.
 *
 * Spring omits ResponseStatusException reasons unless
 * server.error.include-message is set, so a hand-written 400 arrives as the
 * bare words "Bad Request" - true, useless, and identical for six different
 * causes. Until every deployment carries that setting, fall back to the two
 * facts the default error body DOES contain: which path was refused, and
 * anything else the body happens to hold. "Bad Request" tells you nothing;
 * "The server refused /api/v1/forms/create" tells you where to look. (B105)
 *
 * Module scope, and deliberately: as a function nested inside
 * describeHttpError it could not see that `e` had already been narrowed to an
 * HttpErrorResponse - TypeScript will not carry a narrowing into a nested
 * function, because it cannot know when that function runs. Taking the narrowed
 * value as a PARAMETER is the fix. (B106)
 */
function whereItFailed(e: HttpErrorResponse): string {
  const body = (e.error ?? null) as Record<string, unknown> | null;
  const path = typeof body?.['path'] === 'string' ? body['path'] as string : (e.url ?? '');
  const extra = body && typeof body === 'object'
    ? Object.entries(body)
        .filter(([k, v]) => !['timestamp', 'status', 'error', 'path'].includes(k) && v)
        .map(([k, v]) => `${k}: ${String(v)}`)
        .join(' · ')
    : '';
  return [path ? `The server refused ${path}` : 'The server refused that request', extra]
    .filter(Boolean).join(' — ');
}

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
    : (e.error?.message ?? '');

  switch (e.status) {
    case 400: return serverSaid || whereItFailed(e)
      + '. Open the browser console for the full reply, or set '
      + 'ERROR_INCLUDE_MESSAGE=always on the backend so it says why.';
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

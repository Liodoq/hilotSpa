import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, tap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { Connectivity } from './connectivity';
import { API_BASE } from './api.config';

/**
 * Attaches the bearer token to every call to our own API, and keeps the
 * connectivity state honest while it is there.
 *
 * The interceptor is the only place that sees EVERY request and its outcome, so
 * it is the only place that can say "this node is not answering" without each
 * screen having to guess from its own empty list. A status of 0 means no HTTP
 * response at all - a 500 means the server is very much alive and having a bad
 * time, which is a different message.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const net = inject(Connectivity);
  const ours = req.url.startsWith(API_BASE);
  const isAuthCall = req.url.includes('/auth/');

  if (!ours) return next(req);

  const outgoing = (token && !isAuthCall)
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(outgoing).pipe(
    tap(() => net.noteReachable()),
    catchError((err: unknown) => {
      const status = (err as { status?: number })?.status;
      if (status === 0) {
        net.noteUnreachable();
      }
      // B93: the server has refused this token. Drop the session rather than
      // keep re-sending it — otherwise the header goes on showing the client's
      // name while every call behind it fails, which reads as a broken app
      // instead of an expired login. `/auth/` is excluded because a 401 there
      // is a wrong password, not a dead session.
      if (status === 401 && !isAuthCall) {
        auth.expire();
      }
      return throwError(() => err);
    }),
  );
};

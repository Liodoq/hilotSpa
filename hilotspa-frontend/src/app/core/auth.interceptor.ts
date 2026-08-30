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
  const token = inject(AuthService).token();
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
      if ((err as { status?: number })?.status === 0) {
        net.noteUnreachable();
      }
      return throwError(() => err);
    }),
  );
};

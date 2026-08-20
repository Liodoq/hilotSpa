import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';
import { API_BASE } from './api.config';

/**
 * Attaches the bearer token to every call to our own API.
 * The token is never read from the request body — it comes from the session
 * this server signed, which is the whole argument behind Process Rule #5.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).token();
  const ours = req.url.startsWith(API_BASE);
  const isAuthCall = req.url.includes('/auth/');

  if (!token || !ours || isAuthCall) return next(req);

  return next(req.clone({
    setHeaders: { Authorization: `Bearer ${token}` },
  }));
};

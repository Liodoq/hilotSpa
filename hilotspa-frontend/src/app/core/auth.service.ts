import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { API_BASE } from './api.config';
import { PROFILE_CACHE_KEY, ProfileStore } from './profile.store';
import { BOOKING_CACHE_KEY, BookingStore } from './booking.store';
import { BranchContext } from './branch-context';
import { AuthResponse, LoginRequest, RegisterRequest } from './models';

const STORAGE_KEY = 'hilotspa.auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private profile = inject(ProfileStore);
  private booking = inject(BookingStore);
  private branchCtx = inject(BranchContext);

  /** The whole auth response, or null. Read it, never write it from outside. */
  readonly session = signal<AuthResponse | null>(restore());

  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly fullName   = computed(() => this.session()?.fullName ?? '');
  readonly role       = computed(() => this.session()?.role ?? null);
  readonly userId     = computed(() => this.session()?.userId ?? null);
  readonly branchId   = computed(() => this.session()?.branchId ?? null);
  readonly branchName = computed(() => this.session()?.branchName ?? null);

  token(): string | null { return this.session()?.token ?? null; }

  async login(body: LoginRequest): Promise<void> {
    const res = await firstValueFrom(
      this.http.post<AuthResponse>(`${API_BASE}/auth/login`, body));
    this.accept(res);
  }

  async register(body: RegisterRequest): Promise<void> {
    const res = await firstValueFrom(
      this.http.post<AuthResponse>(`${API_BASE}/auth/register`, body));
    this.accept(res);
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
    this.wipeClientData();
    this.router.navigateByUrl('/login');
  }

  /**
   * Drop a session the server has already refused (B93). Called by the
   * interceptor on a 401.
   *
   * Deliberately does NOT navigate. A 401 can arrive while a visitor is reading
   * the landing page — throwing them at a login form they never asked for would
   * be a worse answer than quietly showing them the public menu. The expiry
   * check in restore() catches the common case at startup; this catches the
   * rest: a token revoked mid-session, or one signed with a JWT_SECRET that has
   * since been rotated.
   */
  expire(): void {
    if (this.session() === null) return;
    try { localStorage.removeItem(STORAGE_KEY); } catch { /* private mode */ }
    this.session.set(null);
    this.wipeClientData();
  }

  private accept(res: AuthResponse): void {
    const previous = this.session()?.userId ?? null;

    localStorage.setItem(STORAGE_KEY, JSON.stringify(res));
    this.session.set(res);

    // A different person is now signed in on this browser. Clearing on logout
    // alone was not enough: registering or logging in over an existing session
    // left the previous client's profile and history in memory AND in
    // localStorage, so a brand new account opened onto someone else's
    // demographics. The spa's front desk is a shared machine.
    if (previous !== res.userId) {
      this.wipeClientData();
    }

    // Repopulate from the server for whoever just signed in.
    void this.profile.load();
    void this.booking.load();
  }

  /** Everything cached about a client. Never leave this behind on a switch. */
  private wipeClientData(): void {
    try {
      localStorage.removeItem(PROFILE_CACHE_KEY);
      localStorage.removeItem(BOOKING_CACHE_KEY);
    } catch { /* private mode */ }
    this.profile.clear();
    this.booking.clear();
    // An administrator who switched into a branch must not leave the next
    // person signed in on this machine silently scoped to it. Same family as
    // B70: the front desk is a shared machine.
    this.branchCtx.leave();
  }
}

/**
 * The session stored on this browser, or null if there is not a usable one.
 *
 * B93: this used to return whatever blob was in localStorage. A token that had
 * expired since the last visit still restored, so the header rendered the old
 * client's name and MY BOOKINGS while every authenticated call came back 401 —
 * including the service menu, which then showed "our menu is not loading" to
 * someone the app believed was signed in. The stale half of a session is worse
 * than no session: it looks like the app is broken rather than logged out.
 *
 * This checks `exp` only. It is a DISPLAY decision, not a security one — the
 * browser cannot verify the signature and must never be trusted to. The server
 * remains the only authority on whether a token is good; this just stops the UI
 * from claiming a session the server is going to refuse.
 */
function restore(): AuthResponse | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as AuthResponse;
    if (!session?.token || expiresAt(session.token) <= Date.now()) {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

/**
 * The `exp` claim in milliseconds, or 0 for anything we cannot read.
 *
 * 0 means "treat it as expired". A token whose payload will not decode is not a
 * token we should be sending anywhere.
 */
function expiresAt(token: string): number {
  const parts = token.split('.');
  if (parts.length !== 3) return 0;
  try {
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const pad = b64.length % 4 ? '='.repeat(4 - (b64.length % 4)) : '';
    const exp = (JSON.parse(atob(b64 + pad)) as { exp?: number }).exp;
    return typeof exp === 'number' ? exp * 1000 : 0;
  } catch {
    return 0;
  }
}

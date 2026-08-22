import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { API_BASE } from './api.config';
import { PROFILE_CACHE_KEY, ProfileStore } from './profile.store';
import { BOOKING_CACHE_KEY, BookingStore } from './booking.store';
import { AuthResponse, LoginRequest, RegisterRequest } from './models';

const STORAGE_KEY = 'hilotspa.auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private profile = inject(ProfileStore);
  private booking = inject(BookingStore);

  /** The whole auth response, or null. Read it, never write it from outside. */
  readonly session = signal<AuthResponse | null>(restore());

  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly fullName   = computed(() => this.session()?.fullName ?? '');
  readonly role       = computed(() => this.session()?.role ?? null);
  readonly userId     = computed(() => this.session()?.userId ?? null);
  readonly branchId   = computed(() => this.session()?.branchId ?? null);

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
  }
}

function restore(): AuthResponse | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as AuthResponse) : null;
  } catch {
    return null;
  }
}

import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { API_BASE } from './api.config';
import { AuthResponse, LoginRequest, RegisterRequest } from './models';

const STORAGE_KEY = 'hilotspa.auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

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
    this.router.navigateByUrl('/login');
  }

  private accept(res: AuthResponse): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(res));
    this.session.set(res);
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

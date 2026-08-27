import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_BASE } from './api.config';
import { Branch } from './models';

/**
 * Operational data for the staff and administrator screens.
 *
 * Every route here is branch-scoped on the SERVER, from the JWT. Nothing in this
 * file passes a branch, because a client that can name its own branch is a
 * client that can name someone else's. Filtering here would be decoration.
 */

export interface TherapistDto {
  id: string; firstName: string; lastName: string;
  status: 'AVAILABLE' | 'BUSY' | 'ON_BREAK' | 'OFF_DUTY';
  active: boolean; branchId: string; branchName: string;
}

export interface RoomDto {
  id: string; name: string; active: boolean; branchId: string; branchName: string;
}

export interface AuditRow {
  id: string; action: string; entityType: string; entityId: string;
  actor: string; branch: string | null; details: string | null;
  originNodeId: string; occurredAt: string;
}

export interface ScheduleRow {
  id: string; time: string; start: string; end: string; durationMinutes: number;
  client: string; serviceName: string; therapist: string; room: string; branch: string;
  status: string; paymentStatus: string; source: string;
  hasAssessment: boolean; formId: string | null;
}

/** A treatment on the menu. `active` false means withdrawn, never deleted. */
export interface MassageDto {
  id: string;
  name: string;
  durationMinute: number;
  price: number;
  active: boolean;
}

export interface WalkInRequest {
  serviceId: string;
  /** Local ISO without a zone, e.g. 2026-08-27T14:30:00 — the server works in
   *  the spa's timezone and a browser offset would silently shift the booking. */
  start: string;
  name: string;
  contact?: string | null;
  notes?: string | null;
  idempotencyKey?: string | null;
}

/** One row of the service menu, judged against a specific client if formId given. */
export interface CatalogueEntry {
  serviceId: string; name: string; durationMinutes: number; price: number;
  suitable: boolean; rule: string | null; reason: string | null;
}

export interface AccountRow {
  id: string; firstName: string; lastName: string; middleName: string | null;
  email: string; contact: string | null; address: string | null;
  role: 'CUSTOMER' | 'STAFF' | 'ADMIN';
  branchId: string | null; enabled: boolean; createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class OpsApi {
  private http = inject(HttpClient);

  /** branchId is honoured only for an ADMIN; the server ignores it otherwise. */
  therapists(branchId?: string | null): Promise<TherapistDto[]> {
    return firstValueFrom(this.http.get<TherapistDto[]>(
      `${API_BASE}/therapists${branchId ? `?branchId=${branchId}` : ''}`));
  }

  saveTherapist(id: string | null, body: Partial<TherapistDto>): Promise<TherapistDto> {
    return id
      ? firstValueFrom(this.http.put<TherapistDto>(`${API_BASE}/therapists/${id}`, body))
      : firstValueFrom(this.http.post<TherapistDto>(`${API_BASE}/therapists`, body));
  }

  rooms(branchId?: string | null): Promise<RoomDto[]> {
    return firstValueFrom(this.http.get<RoomDto[]>(
      `${API_BASE}/rooms${branchId ? `?branchId=${branchId}` : ''}`));
  }

  saveRoom(id: string | null, body: Partial<RoomDto>): Promise<RoomDto> {
    return id
      ? firstValueFrom(this.http.put<RoomDto>(`${API_BASE}/rooms/${id}`, body))
      : firstValueFrom(this.http.post<RoomDto>(`${API_BASE}/rooms`, body));
  }

  /** Read-only by design: there is no write route and no delete route. */
  auditLog(action?: string, limit = 100): Promise<AuditRow[]> {
    const q = new URLSearchParams();
    if (action) q.set('action', action);
    q.set('limit', String(limit));
    return firstValueFrom(this.http.get<AuditRow[]>(`${API_BASE}/audit-log?${q}`));
  }

  /** S5 — record a walk-in. The branch comes from the token, never from here. */
  bookWalkIn(body: WalkInRequest): Promise<ScheduleRow | unknown> {
    return firstValueFrom(this.http.post(`${API_BASE}/appointments/walk-in`, body));
  }

  /** The branch day sheet. `date` is yyyy-MM-dd; omitted means today. */
  schedule(date?: string, branchId?: string | null): Promise<ScheduleRow[]> {
    const q = [date ? `date=${date}` : '', branchId ? `branchId=${branchId}` : '']
      .filter(Boolean).join('&');
    return firstValueFrom(this.http.get<ScheduleRow[]>(
      `${API_BASE}/appointments/schedule${q ? '?' + q : ''}`));
  }

  /** The whole service menu, including withdrawn rows. ADMIN maintains it. */
  massages(): Promise<MassageDto[]> {
    return firstValueFrom(this.http.get<MassageDto[]>(`${API_BASE}/massages`));
  }

  saveMassage(id: string | null, body: Partial<MassageDto>): Promise<MassageDto> {
    return id
      ? firstValueFrom(this.http.put<MassageDto>(`${API_BASE}/massages/${id}`, body))
      : firstValueFrom(this.http.post<MassageDto>(`${API_BASE}/massages/create`, body));
  }

  saveBranch(id: string | null, body: { name: string; address: string }): Promise<Branch> {
    return id
      ? firstValueFrom(this.http.put<Branch>(`${API_BASE}/branches/${id}`, body))
      : firstValueFrom(this.http.post<Branch>(`${API_BASE}/branches/create`, body));
  }

  /** Every account. ADMIN only — the server enforces that, not this file. */
  accounts(): Promise<AccountRow[]> {
    return firstValueFrom(this.http.get<AccountRow[]>(`${API_BASE}/users`));
  }

  saveAccount(id: string, body: Partial<AccountRow>): Promise<AccountRow> {
    return firstValueFrom(this.http.put<AccountRow>(`${API_BASE}/users/${id}`, body));
  }

  createAccount(body: Partial<AccountRow> & { password: string }): Promise<AccountRow> {
    return firstValueFrom(this.http.post<AccountRow>(`${API_BASE}/users/create`, body));
  }

  /** The service menu. With formId it comes back annotated for that client. */
  catalogue(formId?: string): Promise<CatalogueEntry[]> {
    const q = formId ? `?formId=${formId}` : '';
    return firstValueFrom(this.http.get<CatalogueEntry[]>(`${API_BASE}/assistant/catalogue${q}`));
  }
}

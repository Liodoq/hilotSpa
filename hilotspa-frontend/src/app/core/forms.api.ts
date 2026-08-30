import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_BASE } from './api.config';
import { AccountMe, AssistantChatResponse, AssistantResponse, BookingModel, Branch, DemographicsModel, FormsModel, PatientIntakeModel } from './models';

@Injectable({ providedIn: 'root' })
export class FormsApi {
  private http = inject(HttpClient);

  createForm(body: FormsModel): Promise<FormsModel> {
    return firstValueFrom(this.http.post<FormsModel>(`${API_BASE}/forms/create`, body));
  }

  /** Ask the assistant to rank the services this client may safely be offered.
   *  Angular never calls n8n on 5678 directly - n8n cannot check a JWT. */
  recommend(formId: string): Promise<AssistantResponse> {
    return firstValueFrom(
      this.http.post<AssistantResponse>(`${API_BASE}/assistant/recommend/${formId}`, {}));
  }

  /**
   * One turn of conversation with the assistant, about a specific assessment.
   *
   * `focusServiceId` is the treatment the conversation has narrowed to. The
   * server sends that one service's WHOLE calendar and samples the rest, so a
   * question about a specific time can be answered truthfully instead of from
   * a two-a-day sample.
   */
  chat(formId: string, message: string, focusServiceId?: string | null):
      Promise<AssistantChatResponse> {
    return firstValueFrom(this.http.post<AssistantChatResponse>(
      `${API_BASE}/assistant/chat/${formId}`,
      { message, focusServiceId: focusServiceId ?? null }));
  }

  /** Book a time the client tapped. Does not go near the model — the slotId is
   *  revalidated server-side against freshly computed availability. */
  confirmSlot(formId: string, slotId: string): Promise<AssistantChatResponse> {
    return firstValueFrom(this.http.post<AssistantChatResponse>(
      `${API_BASE}/assistant/confirm/${formId}`, { slotId }));
  }

  /**
   * Cancel a booking — 2.32.
   *
   * The row is marked CANCELLED server-side, never deleted, so the audit trail
   * and the price at booking survive. The therapist and the room are released
   * because CANCELLED is not a blocking status.
   */
  cancelBooking(id: string, reason?: string): Promise<BookingModel> {
    const q = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return firstValueFrom(
      this.http.delete<BookingModel>(`${API_BASE}/appointments/${id}${q}`));
  }

  /** The caller's own appointments. The server scopes this; never filter client-side. */
  myBookings(): Promise<BookingModel[]> {
    return firstValueFrom(this.http.get<BookingModel[]>(`${API_BASE}/appointments/mine`));
  }

  /** The caller's own account. Identity comes from the token, not a path id. */
  me(): Promise<AccountMe> {
    return firstValueFrom(this.http.get<AccountMe>(`${API_BASE}/users/me`));
  }

  updateMe(body: Partial<AccountMe>): Promise<AccountMe> {
    return firstValueFrom(this.http.put<AccountMe>(`${API_BASE}/users/me`, body));
  }

  changeMyPassword(currentPassword: string, newPassword: string): Promise<void> {
    return firstValueFrom(this.http.put<void>(
      `${API_BASE}/users/me/password`, { currentPassword, newPassword }));
  }

  /** "Nothing has changed" — the server COPIES that assessment into a new
   *  record dated today. It refuses one that is too old (409). */
  reuseForm(id: string): Promise<FormsModel> {
    return firstValueFrom(this.http.post<FormsModel>(`${API_BASE}/forms/${id}/reuse`, {}));
  }

  /** One assessment. The server scopes it: ADMIN any, STAFF their branch,
   *  a customer only their own — anything else answers 404, not 403. */
  form(id: string): Promise<FormsModel> {
    return firstValueFrom(this.http.get<FormsModel>(`${API_BASE}/forms/${id}`));
  }

  /** S4/§H3 — staff record the AFTER-session score for one marked point. */
  recordAfter(intakeId: string, painScoreAfter: number): Promise<PatientIntakeModel> {
    return firstValueFrom(this.http.put<PatientIntakeModel>(
      `${API_BASE}/patient-intake/${intakeId}/after`, { painScoreAfter }));
  }

  myForms(): Promise<FormsModel[]> {
    return firstValueFrom(this.http.get<FormsModel[]>(`${API_BASE}/forms`));
  }

  branches(): Promise<Branch[]> {
    return firstValueFrom(this.http.get<Branch[]>(`${API_BASE}/branches`));
  }

  /** The caller's OWN demographics. The server takes the user from the JWT,
   *  so nothing here can write onto another client's row. */
  saveMyDemographics(body: DemographicsModel): Promise<DemographicsModel> {
    return firstValueFrom(
      this.http.post<DemographicsModel>(`${API_BASE}/demographics/me`, body));
  }

  /** 204 (null) when the profile has not been filled in yet. */
  myDemographics(): Promise<DemographicsModel | null> {
    return firstValueFrom(
      this.http.get<DemographicsModel | null>(`${API_BASE}/demographics/me`));
  }

  /** Front desk only: walk-in clients with no account. STAFF/ADMIN. */
  createDemographics(body: DemographicsModel): Promise<DemographicsModel> {
    return firstValueFrom(
      this.http.post<DemographicsModel>(`${API_BASE}/demographics/create`, body));
  }
}

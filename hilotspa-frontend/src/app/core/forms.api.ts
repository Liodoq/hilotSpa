import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_BASE } from './api.config';
import { Branch, DemographicsModel, FormsModel } from './models';

@Injectable({ providedIn: 'root' })
export class FormsApi {
  private http = inject(HttpClient);

  createForm(body: FormsModel): Promise<FormsModel> {
    return firstValueFrom(this.http.post<FormsModel>(`${API_BASE}/forms/create`, body));
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

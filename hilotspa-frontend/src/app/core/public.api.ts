import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_BASE } from './api.config';

/**
 * The only calls that work without signing in.
 *
 * Kept in their own file rather than folded into OpsApi so that "what a
 * stranger can read" is one short, reviewable list. Nothing here takes a
 * parameter, so nothing here can be made to answer a question about a
 * particular client.
 */

/** One treatment as the public menu shows it. No protocol verdict — that is a
 *  judgement about a specific person and is never computed for a visitor. */
export interface PublicService {
  id: string;
  name: string;
  durationMinutes: number;
  price: number;
  imageName: string | null;
}

/** A therapist as a stranger sees them: first name and sex, nothing else. */
export interface PublicTherapist {
  firstName: string;
  sex: string | null;
}

export interface PublicSpa {
  name: string;
  tagline: string;
  address: string;
  phone: string;
  hours: string;
  facebook: string;
  mapsUrl: string;
  services: PublicService[];
  therapists: PublicTherapist[];
}

@Injectable({ providedIn: 'root' })
export class PublicApi {
  private http = inject(HttpClient);

  /** The landing page in one call: the spa's details and its live menu. */
  spa(): Promise<PublicSpa> {
    return firstValueFrom(this.http.get<PublicSpa>(`${API_BASE}/public/spa`));
  }

  services(): Promise<PublicService[]> {
    return firstValueFrom(this.http.get<PublicService[]>(`${API_BASE}/public/services`));
  }
}

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
  /** The spa's own words, or null. The detail page has its own fallback -
   *  nothing here is ever generated, because a description the system wrote
   *  itself would be a clinical claim nobody authored. */
  description: string | null;
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

/**
 * Which node served this page, and which branch it writes for.
 *
 * branchId is null when the node has not been told which branch it owns. That
 * is a legitimate single-node configuration, so the caller falls back to the
 * old behaviour rather than refusing - but on a two-node deployment it means
 * the node is misconfigured, and the server says so in its startup log.
 */
export interface NodeIdentity {
  nodeId: string;
  nodeName: string;
  branchId: string | null;
  branchName: string | null;
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

  /** This node's own identity. Unauthenticated: the page has to name the
   *  branch before anybody signs in. */
  node(): Promise<NodeIdentity> {
    return firstValueFrom(this.http.get<NodeIdentity>(`${API_BASE}/public/node`));
  }
}

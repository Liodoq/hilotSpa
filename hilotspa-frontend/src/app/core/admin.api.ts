import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_BASE } from './api.config';

/** A1 — the administrator's aggregate. Counted server-side on every request. */

export interface NodeCard {
  branchId: string; branchName: string; nodeId: string; thisNode: boolean;
  bookingsToday: number; therapists: number; therapistsAvailable: number;
  rooms: number; assessmentsThisWeek: number; lastWrite: string | null;
}

export interface ComplaintCount { label: string; count: number; pct: number; }

export interface AssistantStats {
  calls: number; ok: number; failed: number;
  rejectedSuggestions: number; returnedSuggestions: number;
  rejectionRatePct: number; note: string;
}

/** What is stopping a real evaluation. Counted on the server, like everything else. */
export interface Readiness {
  protocolRules: number;
  unsignedRules: number;
  contraindications: number;
  servicesOnSale: number;
  servicesWithoutPrice: number;
}

export interface Overview {
  generatedAt: string;
  bookingsToday: number; bookingsThisWeek: number;
  assessmentsThisWeek: number; assessmentsTotal: number;
  nodesOnline: number; nodesTotal: number;
  nodes: NodeCard[];
  topComplaints: ComplaintCount[];
  assistant: AssistantStats;
  readiness: Readiness;
}

/** X2 — one row of the signed contraindication table. */
export interface ProtocolRow {
  id: string; serviceId: string; serviceName: string;
  condition: string; conditionLabel: string;
  rule: 'INDICATED' | 'CONTRAINDICATED';
  rationale: string | null; authoredBy: string; signed: boolean; createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private http = inject(HttpClient);

  overview(): Promise<Overview> {
    return firstValueFrom(this.http.get<Overview>(`${API_BASE}/admin/overview`));
  }

  protocols(): Promise<ProtocolRow[]> {
    return firstValueFrom(this.http.get<ProtocolRow[]>(`${API_BASE}/protocols`));
  }

  /** The server refuses a change with no name against it — that is the point. */
  updateProtocol(id: string, body: { rule?: string; rationale?: string; authoredBy: string }):
      Promise<ProtocolRow> {
    return firstValueFrom(this.http.put<ProtocolRow>(`${API_BASE}/protocols/${id}`, body));
  }
}

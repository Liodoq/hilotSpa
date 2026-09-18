import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
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

/** Operational readiness. OK = working · DEGRADED = running but broken somewhere · DOWN = unusable. */
export type HealthState = 'OK' | 'DEGRADED' | 'DOWN';

export interface HealthCheck { name: string; state: HealthState; detail: string; }

export interface Health {
  state: HealthState;
  checkedAt: string;
  nodeId: string;
  timezone: string;
  summary: string;
  checks: HealthCheck[];
}

/** X2 — one row of the signed contraindication table. */
export interface ProtocolRow {
  id: string; serviceId: string; serviceName: string;
  condition: string; conditionLabel: string;
  rule: 'INDICATED' | 'CONTRAINDICATED';
  rationale: string | null; authoredBy: string; signed: boolean; createdAt: string;
}

/**
 * A3 - the administrator's reports.
 *
 * `basis` and `countedNote` are not decoration. A ranking of treatments means
 * nothing unless the reader can tell whether cancelled bookings are in it, so
 * the server states what it counted and this screen prints that sentence
 * verbatim rather than paraphrasing it.
 */
export type ReportBasis = 'COMPLETED' | 'BOOKED';

export interface ServiceRow {
  serviceId: string; name: string;
  /** Not optional: the spa sells the same treatment at 60 and at 90, and a
   *  table of names alone shows it twice and reads as a duplicated row. */
  durationMinutes: number;
  visits: number; pct: number; revenue: number;
}

/**
 * One branch's line in the comparison.
 *
 * `nodeId` is read from the BOOKINGS, not from whichever node answered the
 * request - a branch labelled with the asking node is a guess, and it is wrong
 * in exactly the case two nodes exist for.
 */
export interface BranchRow {
  branchId: string; name: string; nodeId: string;
  visits: number; pct: number; revenue: number;
  /** 1 is busiest. Ties share a rank. */
  rank: number;
}

export interface MonthRow {
  /** sortable, e.g. "2026-09" */
  month: string;
  /** readable, e.g. "Sep 2026" */
  label: string;
  visits: number;
  revenue: number;
  /** true for the busiest month, and for every month tying it */
  peak: boolean;
}

export interface Reports {
  generatedAt: string;
  from: string; to: string;
  branchId: string | null; branchName: string;
  basis: ReportBasis;
  countedNote: string;
  visitsCounted: number;
  revenueCounted: number;
  services: ServiceRow[];
  months: MonthRow[];
  /** empty for a staff account and for a single-branch spa */
  branches: BranchRow[];
  /** null when the range is empty or two months tie - then peakNote says why */
  peakMonth: MonthRow | null;
  peakNote: string | null;
}

/**
 * A4 - the notification log.
 *
 * One row per notification the system TRIED to send, not per success. A log
 * that records only what worked cannot answer "I never got a reminder, did you
 * send one?" - in it, silence and failure look the same.
 */
export type NotificationStatus = 'SENDING' | 'SENT' | 'FAILED' | 'SKIPPED';

export interface NotificationRow {
  id: string; appointmentId: string;
  client: string; serviceName: string; branchName: string;
  visitAt: string;
  kind: string; channel: string; status: NotificationStatus;
  recipient: string | null;
  attempts: number;
  /** why it was skipped, or what the mail server said. null on a clean send */
  detail: string | null;
  originNodeId: string;
  createdAt: string;
  /** set on SENT and nothing else */
  sentAt: string | null;
}

export interface RunResult { visitDay: string; sent: number; note: string; }

/**
 * One visit due on a day, and whether it has been told yet.
 *
 * NOT the log. The log answers "what did we send"; this answers "who is booked
 * tomorrow and which of them still has not heard from us" - and a client nobody
 * has attempted has no log row at all, so they are invisible in exactly the
 * list you would use to notice them.
 */
export interface DueRow {
  appointmentId: string;
  client: string; serviceName: string; branchName: string;
  visitAt: string;
  recipient: string | null;
  /** null when nothing has ever been attempted for this visit */
  status: NotificationStatus | null;
  attempts: number;
  detail: string | null;
  sentAt: string | null;
  /** false only when there is no address. An already-SENT visit IS sendable. */
  sendable: boolean;
  blockedReason: string | null;
}

/** One line of an import, and what became of it. */
export interface ImportLine {
  line: number; serviceName: string; condition: string;
  /** CREATED | UPDATED | UNCHANGED | REJECTED */
  outcome: string;
  /** set on REJECTED, and the only thing that explains the count */
  problem: string | null;
}

export interface ImportResult {
  created: number; updated: number; unchanged: number; rejected: number;
  note: string;
  lines: ImportLine[];
}

/**
 * How much of the catalogue the protocol table covers.
 *
 * A service with no rule is still offerable — that is deliberate, and it is
 * exactly why this has to be counted somewhere a person will see it.
 */
export interface SignResult { signed: number; alreadySigned: number; note: string; }

export interface Coverage {
  services: number;
  servicesWithNoRule: number;
  rules: number;
  unsigned: number;
  contraindications: number;
  uncovered: string[];
}

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private http = inject(HttpClient);

  overview(): Promise<Overview> {
    return firstValueFrom(this.http.get<Overview>(`${API_BASE}/admin/overview`));
  }

  /** Answers 503 when the system is unusable, so a monitor need not read the body. */
  health(): Promise<Health> {
    return firstValueFrom(this.http.get<Health>(`${API_BASE}/admin/health`));
  }

  /** Omitted params mean "the last twelve months" and "every branch". */
  reports(q: { from?: string; to?: string; branchId?: string } = {}): Promise<Reports> {
    let p = new HttpParams();
    if (q.from) { p = p.set('from', q.from); }
    if (q.to) { p = p.set('to', q.to); }
    if (q.branchId) { p = p.set('branchId', q.branchId); }
    return firstValueFrom(this.http.get<Reports>(`${API_BASE}/reports`, { params: p }));
  }

  /** branchId is honoured for an administrator only; staff are pinned by token. */
  notifications(branchId?: string | null): Promise<NotificationRow[]> {
    return firstValueFrom(this.http.get<NotificationRow[]>(
      `${API_BASE}/notifications`, { params: this.scoped(branchId) }));
  }

  /** Who is booked that day, and which of them has been told. */
  dueReminders(day?: string, branchId?: string | null): Promise<DueRow[]> {
    let p = this.scoped(branchId);
    if (day) { p = p.set('day', day); }
    return firstValueFrom(
      this.http.get<DueRow[]>(`${API_BASE}/notifications/due`, { params: p }));
  }

  /**
   * Send ONE reminder now.
   *
   * Deliberately safe to press on a visit already reminded. The whole-day run
   * refuses a repeat because it must; a person pressing a button beside one
   * named client is asking for something else entirely.
   */
  sendOneReminder(appointmentId: string, branchId?: string | null): Promise<RunResult> {
    return firstValueFrom(this.http.post<RunResult>(
      `${API_BASE}/notifications/due/${appointmentId}`, {},
      { params: this.scoped(branchId) }));
  }

  private scoped(branchId?: string | null): HttpParams {
    return branchId ? new HttpParams().set('branchId', branchId) : new HttpParams();
  }

  /**
   * Run the day-before reminder now, for a named day.
   *
   * Safe to press twice. The second press sends nothing, because the unique
   * constraint on (appointment, kind) is what stops a client being mailed
   * twice - "Sent 0" is that working, not a fault.
   */
  runReminders(day?: string, branchId?: string | null): Promise<RunResult> {
    let p = branchId ? new HttpParams().set('branchId', branchId) : new HttpParams();
    if (day) { p = p.set('day', day); }
    return firstValueFrom(
      this.http.post<RunResult>(`${API_BASE}/notifications/run`, {}, { params: p }));
  }

  protocols(): Promise<ProtocolRow[]> {
    return firstValueFrom(this.http.get<ProtocolRow[]>(`${API_BASE}/protocols`));
  }

  /** The signature travels as a query parameter: DELETE bodies are handled
   *  inconsistently across the stack, and it is a name, not a secret. */
  deleteProtocol(id: string, authoredBy: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${API_BASE}/protocols/${id}`,
      { params: new HttpParams().set('authoredBy', authoredBy) }));
  }

  /**
   * Record that a practitioner signed the printed sheet.
   *
   * Not an import. One person signed one sheet covering every unsigned rule,
   * and no rule's verdict changes — only who is answerable for it.
   */
  signProtocols(authoredBy: string, note: string): Promise<SignResult> {
    return firstValueFrom(
      this.http.post<SignResult>(`${API_BASE}/protocols/sign`, { authoredBy, note }));
  }

  protocolCoverage(): Promise<Coverage> {
    return firstValueFrom(this.http.get<Coverage>(`${API_BASE}/protocols/coverage`));
  }

  /** Author one rule. The server refuses it without a name against it. */
  createProtocol(body: {
    serviceName: string; condition: string; rule: string;
    rationale?: string; authoredBy: string;
  }): Promise<ProtocolRow> {
    return firstValueFrom(this.http.post<ProtocolRow>(`${API_BASE}/protocols`, body));
  }

  /**
   * Load a file of rules.
   *
   * The CSV goes up as a string rather than multipart: it is a few kilobytes,
   * the browser has already read it, and an upload pipeline would be more
   * machinery than the payload.
   */
  importProtocols(csv: string, authoredBy: string): Promise<ImportResult> {
    return firstValueFrom(
      this.http.post<ImportResult>(`${API_BASE}/protocols/import`, { csv, authoredBy }));
  }

  /** The server refuses a change with no name against it — that is the point. */
  updateProtocol(id: string, body: { rule?: string; rationale?: string; authoredBy: string }):
      Promise<ProtocolRow> {
    return firstValueFrom(this.http.put<ProtocolRow>(`${API_BASE}/protocols/${id}`, body));
  }
}

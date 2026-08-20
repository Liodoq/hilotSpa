import { Injectable, computed, signal } from '@angular/core';
import { DemoAppointment, NEXT_VISIT, PAST_VISITS } from './demo';

/**
 * The client's upcoming visit and their session history.
 *
 * Persisted to localStorage so a cancellation survives a refresh. Without that
 * the cancel lived only in a signal, so reloading brought the booking back —
 * which reads as "the cancel did not work".
 *
 * TODO Sprint 2 — DELETE /api/v1/appointments/{id} and GET ?mine replace all of
 * this. The shape is already what the endpoints will return.
 */
export interface SessionLog {
  id: string;
  date: string;
  service: string;
  minutes: number;
  complaint: string;
  before: number | null;
  after: number | null;
  therapist: string;
  room: string;
  branch: string;
  notes: string;
}

const KEY = 'hilotspa.booking';

const LOGS: SessionLog[] = PAST_VISITS.map((v, i) => ({
  id: `s${i + 1}`,
  date: v.date,
  service: v.service,
  minutes: v.service === 'Full Body Massage' ? 90 : 60,
  complaint: v.complaint,
  before: v.before,
  after: v.after,
  therapist: i === 2 ? 'Josie Delgado' : 'Marites Bonifacio',
  room: i === 1 ? 'Room 1' : 'Room 2',
  branch: 'Bulan, Sorsogon',
  notes: v.before === null
    ? 'Relaxation session. No pain recorded before or after.'
    : 'Tightness eased over the session. Advised warm compress at home and a follow-up in two weeks.',
}));

@Injectable({ providedIn: 'root' })
export class BookingStore {
  readonly upcoming = signal<DemoAppointment | null>(restore());
  readonly history = signal<SessionLog[]>(LOGS);
  readonly hasUpcoming = computed(() => this.upcoming() !== null);

  cancel(): DemoAppointment | null {
    const v = this.upcoming();
    this.upcoming.set(null);
    localStorage.setItem(KEY, 'CANCELLED');
    return v;
  }

  restoreDemo(): void {
    this.upcoming.set(NEXT_VISIT);
    localStorage.removeItem(KEY);
  }

  find(id: string): SessionLog | undefined {
    return this.history().find(s => s.id === id);
  }
}

function restore(): DemoAppointment | null {
  try {
    return localStorage.getItem(KEY) === 'CANCELLED' ? null : NEXT_VISIT;
  } catch {
    return NEXT_VISIT;
  }
}

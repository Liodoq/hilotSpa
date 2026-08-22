import { Injectable, computed, inject, signal } from '@angular/core';
import { DemoAppointment } from './demo';
import { FormsApi } from './forms.api';
import { FormsModel } from './models';
import { PainPoint } from './assessment.store';

/**
 * The client's own session history, read from the server.
 *
 * It used to be seeded from demo.ts at module load, which meant EVERY account —
 * including a brand new one — opened onto the same three fictional sessions and
 * the same fictional body map. A new client saw someone else's clinical record.
 *
 * Now it loads GET /api/v1/forms, which the backend already scopes: ADMIN sees
 * all, STAFF sees their branch, a CUSTOMER sees only their own. An account with
 * no assessments correctly shows nothing.
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
  duration: string;
  notes: string;
  points: PainPoint[];
}

const KEY = 'hilotspa.booking';

/** Exported so logout can clear it. */
export const BOOKING_CACHE_KEY = KEY;

@Injectable({ providedIn: 'root' })
export class BookingStore {
  private api = inject(FormsApi);

  /** No appointments endpoint exists yet, so there is genuinely nothing to show.
   *  TODO Sprint 2 — GET /api/v1/appointments?mine (task 2.18/2.19). */
  readonly upcoming = signal<DemoAppointment | null>(null);

  readonly history = signal<SessionLog[]>([]);
  readonly loaded = signal(false);
  readonly hasUpcoming = computed(() => this.upcoming() !== null);

  /** The body map on the wellness profile: the client's OWN most recent marks. */
  readonly lastPoints = computed<PainPoint[]>(() => this.history()[0]?.points ?? []);

  async load(): Promise<void> {
    try {
      const forms = await this.api.myForms();
      const logs = (forms ?? [])
        .slice()
        .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
        .map(toLog);
      this.history.set(logs);
    } catch {
      // Not logged in, or offline. Show nothing rather than someone else's data.
      this.history.set([]);
    } finally {
      this.loaded.set(true);
    }
  }

  cancel(): DemoAppointment | null {
    const v = this.upcoming();
    this.upcoming.set(null);
    try { localStorage.setItem(KEY, 'CANCELLED'); } catch { /* private mode */ }
    return v;
  }

  find(id: string): SessionLog | undefined {
    return this.history().find(s => s.id === id);
  }

  clear(): void {
    this.history.set([]);
    this.upcoming.set(null);
    this.loaded.set(false);
    try { localStorage.removeItem(KEY); } catch { /* private mode */ }
  }
}

// --- mapping -------------------------------------------------------------

function toLog(f: FormsModel): SessionLog {
  const points = (f.painPoints ?? []).map((p, i) => ({
    key: `p${i}`,
    hotspotId: '',
    view: p.bodyView,
    x: p.coordinateX,
    y: p.coordinateY,
    region: p.anatomicalRegion,
    side: p.side,
    score: p.painScoreBefore,
    qualities: [],
  })) as unknown as PainPoint[];

  const befores = (f.painPoints ?? []).map(p => p.painScoreBefore).filter(n => n != null);
  const afters  = (f.painPoints ?? []).map(p => p.painScoreAfter).filter(n => n != null) as number[];

  return {
    id: f.id ?? '',
    date: formatDate(f.createdAt),
    // Until appointments exist there is no service, therapist or room to name.
    // Writing "—" is honest; inventing one is how the demo data problem started.
    service: 'Pre-assessment',
    minutes: 0,
    complaint: f.intent === 'LEISURE'
      ? 'Here to relax'
      : label(f.mainComplaint) || f.mainComplaintOther || 'Not stated',
    before: befores.length ? Math.max(...befores as number[]) : null,
    after:  afters.length  ? Math.max(...afters) : null,
    therapist: '—',
    room: '—',
    branch: '—',
    duration: f.mainComplaintDuration ?? '',
    notes: f.remarks ?? '',
    points,
  };
}

/** LOWER_BACK_PAIN -> Lower Back Pain. Enum constants are not words. */
function label(v: string | null | undefined): string {
  if (!v) return '';
  return v.split('_').map(w => w.charAt(0) + w.slice(1).toLowerCase()).join(' ');
}

function formatDate(iso?: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

import { Injectable, computed, inject, signal } from '@angular/core';
import { FormsApi } from './forms.api';
import { AssessmentIntent, BookingModel, FormsModel, PRESSURES, safetyFlagLabel } from './models';
import { PainPoint, toPainPoint } from './assessment.store';

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
/**
 * The client's next visit, as the home screen draws it.
 *
 * Kept here rather than in a shared placeholder file: this is now the only
 * shape the store hands out, and the placeholder module it used to live in
 * has been retired.
 */
export interface UpcomingVisit {
  /** The appointment's real id. `ref` is the short form a client reads out; this
   *  is the one the cancel endpoint needs. */
  id: string;
  ref: string; service: string; minutes: number; date: string; day: string; month: string;
  dayNum: string; time: string; therapist: string; room: string; branch: string;
  price: number; status: 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';
}

export interface SessionLog {
  id: string;
  /**
   * PAIN or LEISURE — why the client came, as they answered it themselves.
   *
   * It was dropped in toLog(), which is why the booking assistant greeted a
   * client who had tapped "I am here to relax" with "Based on your assessment":
   * the only record the screen had of them no longer said which path they took.
   * `complaint` already encodes it as the string "Here to relax", but reading
   * intent back out of a display label is how a UI ends up matching on prose.
   */
  intent: AssessmentIntent;
  /** H9 - the safety checklist as ticked, so the record and the printed sheet
   *  can show what the practitioner was working around. */
  flags: string[];
  pressure: string | null;
  date: string;
  /** Raw ISO timestamp. `date` is for display; this is for arithmetic. */
  createdAt: string;
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

  /**
   * The visit this assessment produced, when there is one.
   *
   * Null means the client filled the form and never booked. That row is real
   * data - it is a person who assessed and did not come - but it is not a
   * SESSION, and listing it under "past sessions" was the screen calling an
   * intention a visit.
   */
  appointmentId: string | null;
  /** CONFIRMED / COMPLETED / NO_SHOW / CANCELLED, or null with no visit. */
  status: string | null;
  /** Each pain point, so the client can score how it feels afterwards. */
  scores: { id: string; region: string; before: number | null; after: number | null }[];
}

const KEY = 'hilotspa.booking';

/** Exported so logout can clear it. */
export const BOOKING_CACHE_KEY = KEY;

@Injectable({ providedIn: 'root' })
export class BookingStore {
  private api = inject(FormsApi);

  /** The client's real next visit, from GET /appointments/mine. */
  readonly upcoming = signal<UpcomingVisit | null>(null);

  /** All of them, soonest first. The home screen shows the next; My Bookings
   *  shows every one — a page that silently drops the second booking is the
   *  screen disagreeing with the database. */
  readonly upcomingAll = signal<UpcomingVisit[]>([]);

  readonly history = signal<SessionLog[]>([]);
  readonly loaded = signal(false);

  /**
   * True when the last load could not reach the server.
   *
   * Without this an empty list means two completely different things - "you
   * have no bookings" and "we could not ask" - and the screen picks the
   * frightening one. A client at the spa reading "no upcoming visit" because
   * the wifi dropped will go and queue at the counter.
   */
  readonly unreachable = signal(false);
  readonly hasUpcoming = computed(() => this.upcoming() !== null);

  /** The body map on the wellness profile: the client's OWN most recent marks. */
  readonly lastPoints = computed<PainPoint[]>(() => this.history()[0]?.points ?? []);

  async load(): Promise<void> {
    let reachable = true;
    try {
      const forms = await this.api.myForms();
      const logs = (forms ?? [])
        .slice()
        .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
        .map(toLog);
      this.history.set(logs);
    } catch {
      // Not logged in, or offline. Show nothing rather than someone else's data -
      // but RECORD that we could not ask, so the screen can say so.
      this.history.set([]);
      reachable = false;
    }

    // Appointments are a separate call and must not be lost if it fails — the
    // assessment history is still worth showing on its own.
    try {
      const now = Date.now();
      const visits = (await this.api.myBookings() ?? [])
        .filter(b => new Date(b.start).getTime() >= now && b.status !== 'CANCELLED')
        .sort((a, b) => new Date(a.start).getTime() - new Date(b.start).getTime())
        .map(toVisit);
      this.upcomingAll.set(visits);
      this.upcoming.set(visits[0] ?? null);
    } catch {
      this.upcomingAll.set([]);
      this.upcoming.set(null);
      reachable = false;
    }

    this.unreachable.set(!reachable);
    this.loaded.set(true);
  }

  /**
   * Cancel a visit for real — 2.32.
   *
   * This used to be a local dismissal that hid the card and said so, because
   * there was no endpoint. There is one now, so the therapist and the room are
   * genuinely released and the row is marked CANCELLED rather than deleted.
   *
   * The list is rebuilt from the SERVER afterwards, never patched optimistically:
   * if the server refused — the visit has already started, someone else cancelled
   * it first — the screen must show what the database says, not what the tap
   * hoped for. That disagreement is the bug a client discovers by turning up.
   */
  async cancel(appointmentId: string, reason?: string): Promise<void> {
    await this.api.cancelBooking(appointmentId, reason);
    try { localStorage.removeItem(KEY); } catch { /* private mode */ }
    await this.load();
  }

  find(id: string): SessionLog | undefined {
    return this.history().find(s => s.id === id);
  }

  clear(): void {
    this.history.set([]);
    this.upcoming.set(null);
    this.upcomingAll.set([]);
    this.loaded.set(false);
    this.unreachable.set(false);
    try { localStorage.removeItem(KEY); } catch { /* private mode */ }
  }
}

// --- mapping -------------------------------------------------------------

/** BookingModel (the API shape) -> UpcomingVisit (what the home card draws). */
function toVisit(b: BookingModel): UpcomingVisit {
  const d = new Date(b.start);
  return {
    id: b.id,
    ref: b.id.slice(0, 8).toUpperCase(),
    service: b.serviceName,
    minutes: b.durationMinutes,
    date: b.label,
    day: d.toLocaleDateString('en-GB', { weekday: 'long' }),
    month: d.toLocaleDateString('en-GB', { month: 'short' }).toUpperCase(),
    dayNum: String(d.getDate()),
    time: b.label,
    therapist: b.therapist,
    room: b.room,
    branch: b.branch,
    price: b.price,
    status: (b.status === 'CANCELLED' ? 'CANCELLED'
      : b.status === 'COMPLETED' ? 'COMPLETED' : 'CONFIRMED'),
  };
}

function toLog(f: FormsModel): SessionLog {
  // Was an inline copy of this mapping that read coordinateX as a percentage
  // when the server stores 0-1000, so every marker landed ten times too far
  // right and down (B125). One mapper now, beside the scale it undoes.
  const points: PainPoint[] = (f.painPoints ?? []).map(toPainPoint);

  const befores = (f.painPoints ?? []).map(p => p.painScoreBefore).filter(n => n != null);
  const afters  = (f.painPoints ?? []).map(p => p.painScoreAfter).filter(n => n != null) as number[];

  // B92 - the visit this assessment produced, when the server found one.
  // Before this the history could only ever describe the FORM, so a finished
  // session read "Pre-assessment · 0 minutes · — · — · —" even though the
  // appointment, the therapist and the room were all sitting in the database
  // behind a foreign key nothing read.
  const v = f.visit ?? null;

  return {
    appointmentId: v ? v.appointmentId : null,
    status: v ? v.status : null,
    scores: (f.painPoints ?? [])
      .filter(p => p.id)
      .map(p => ({
        id: p.id as string,
        region: p.anatomicalRegion,
        before: p.painScoreBefore ?? null,
        after: p.painScoreAfter ?? null,
      })),
    id: f.id ?? '',
    intent: f.intent,
    flags: (f.safetyFlags ?? []).map(safetyFlagLabel),
    pressure: f.pressurePreference
      ? (PRESSURES.find(p => p.value === f.pressurePreference)?.label ?? null)
      : null,
    date: v ? v.label : formatDate(f.createdAt),
    createdAt: f.createdAt ?? '',
    // Still honest when there is no visit: this client filled the form and never
    // booked, and saying "Pre-assessment" is exactly right for that row.
    service: v ? v.serviceName : 'Pre-assessment',
    minutes: v ? v.durationMinutes : 0,
    complaint: f.intent === 'LEISURE'
      ? 'Here to relax'
      : label(f.mainComplaint) || f.mainComplaintOther || 'Not stated',
    before: befores.length ? Math.max(...befores as number[]) : null,
    after:  afters.length  ? Math.max(...afters) : null,
    therapist: v && v.therapist ? v.therapist : '—',
    room: v && v.room ? v.room : '—',
    branch: v && v.branch ? v.branch : '—',
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

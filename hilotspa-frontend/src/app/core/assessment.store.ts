import { Injectable, computed, inject, signal } from '@angular/core';
import {
  AnatomicalRegion, AssessmentIntent, BodyView, ComplaintType,
  FormsModel, PatientIntakeModel, PressurePreference, SafetyFlag, Sex,
} from './models';
import { ProfileStore } from './profile.store';
import { Side } from './body-hotspots';

/** One marker on the body map, before it becomes a PatientIntakeModel. */
/**
 * The server stores a marker's position as 0-1000, not 0-100.
 *
 * B125, and it cost three attempts to find because it never looked like a data
 * bug. The write scaled up ("resolution-independent") and NOTHING scaled back
 * down: two separate readers - the homepage wellness card and the admin View
 * assessment page - both took the stored number for a percentage. A marker at
 * 35% of the width came back as 350, and `left: 350%` puts it three and a half
 * figures to the right of the body, which is exactly where Liodoq kept seeing
 * them: floating at the bottom-right of the card, apparently escaping their
 * container.
 *
 * Two CSS fixes were attempted against that symptom and neither could have
 * worked - `.figure { position: relative }` had been correct the whole time.
 * Moving the marks into the svg changed the symptom from "wildly misplaced" to
 * "invisible", because 350 and 880 fall outside a 300x640 viewBox and the svg
 * clips them.
 *
 * So the scale now lives in ONE place, with its inverse beside it, and every
 * reader goes through toPainPoint. A unit written on one side of a boundary
 * and assumed on the other is not a convention, it is a bug waiting for a
 * second reader.
 */
const COORD_SCALE = 10;

/**
 * One stored marker, in the shape BodyMap draws.
 *
 * The single place a PatientIntakeModel becomes a PainPoint. Both readers used
 * to do this inline, identically, and both got the scale wrong - and the
 * `as unknown as PainPoint[]` cast each of them carried is what kept the
 * compiler quiet about it.
 */
export function toPainPoint(p: PatientIntakeModel, i: number): PainPoint {
  return {
    key: p.id ?? `p${i}`,
    hotspotId: '',
    view: p.bodyView,
    x: (p.coordinateX ?? 0) / COORD_SCALE,
    y: (p.coordinateY ?? 0) / COORD_SCALE,
    region: p.anatomicalRegion,
    side: (p.side ?? 'CENTRE') as Side,
    score: p.painScoreBefore ?? 0,
    qualities: [],
  };
}

export interface PainPoint {
  key: string;             // client-side only, for tracking in @for
  hotspotId: string;       // which fixed anatomical position was tapped
  view: BodyView;
  x: number;               // 0-100, percentage of the figure's width
  y: number;               // 0-100, percentage of the figure's height
  region: AnatomicalRegion;
  side: Side;              // the CLIENT's own left/right — the form's L/R columns
  score: number;           // 1-10
  qualities: string[];     // Pain / Stiff / Weak / Numb, from the paper form
}

export interface AssessmentDraft {
  intent: AssessmentIntent | null;
  // Demographics are NOT here. They belong to the person, not the visit — see
  // ProfileStore. Asking for them every booking was the bug, not the feature.
  points: PainPoint[];
  complaints: ComplaintType[];
  mainComplaint: ComplaintType | null;
  mainComplaintOther: string | null;
  duration: string | null;
  hadIllness: boolean | null;
  illnessDetail: string;
  hasTherapy: boolean | null;
  therapyKind: string;
  therapyWhen: string;
  flags: SafetyFlag[];
  pressure: PressurePreference | null;
  /** null = no preference. Honoured in availability AND at assignment. */
  therapist: Sex | null;
  consented: boolean;
}

const EMPTY: AssessmentDraft = {
  intent: null, points: [], complaints: [], mainComplaint: null,
  mainComplaintOther: null, duration: null, hadIllness: null, illnessDetail: '',
  hasTherapy: null, therapyKind: '', therapyWhen: '', flags: [], pressure: null,
  therapist: null,
  consented: false,
};

/**
 * C2 through C7 are ONE form, not six.
 *
 * Forms is an aggregate root on the backend — there is a single write path and
 * a pain point cannot exist without its parent assessment. Six components each
 * calling the API would contradict that design. So the whole draft lives here
 * and is posted exactly once, from C7.
 */
@Injectable({ providedIn: 'root' })
export class AssessmentStore {
  private profileStore = inject(ProfileStore);
  readonly draft = signal<AssessmentDraft>(structuredClone(EMPTY));

  /** Set when someone picks a service on C8 before assessing. The assistant
   *  opens with it instead of asking a question it already has the answer to.
   *  Cleared with the draft. */
  readonly wantedService = signal<string | null>(null);

  readonly isLeisure = computed(() => this.draft().intent === 'LEISURE');
  readonly pointCount = computed(() => this.draft().points.length);

  patch(part: Partial<AssessmentDraft>): void {
    this.draft.update(d => ({ ...d, ...part }));
  }

  reset(): void { this.draft.set(structuredClone(EMPTY)); }
  resetAll(): void { this.reset(); this.wantedService.set(null); }

  /* ---- pain points ---- */
  addPoint(p: Omit<PainPoint, 'key'>): string {
    const key = `p${Date.now()}${Math.random().toString(36).slice(2, 7)}`;
    this.draft.update(d => ({ ...d, points: [...d.points, { ...p, key }] }));
    return key;
  }

  updatePoint(key: string, part: Partial<PainPoint>): void {
    this.draft.update(d => ({
      ...d,
      points: d.points.map(p => (p.key === key ? { ...p, ...part } : p)),
    }));
  }

  removePoint(key: string): void {
    this.draft.update(d => ({ ...d, points: d.points.filter(p => p.key !== key) }));
  }

  /* ---- complaints ---- */
  toggleComplaint(c: ComplaintType): void {
    this.draft.update(d => {
      const on = d.complaints.includes(c);
      const complaints = on ? d.complaints.filter(x => x !== c) : [...d.complaints, c];

      let mainComplaint = d.mainComplaint;
      // Unticking the chief complaint clears it — it can no longer be chief.
      if (on && mainComplaint === c) mainComplaint = null;
      // With exactly one condition ticked there is nothing to choose between,
      // so choosing it for them removes a step that only ever has one answer.
      if (complaints.length === 1) mainComplaint = complaints[0];
      if (complaints.length === 0) mainComplaint = null;

      return { ...d, complaints, mainComplaint };
    });
  }

  toggleFlag(f: SafetyFlag): void {
    this.draft.update(d => ({
      ...d,
      flags: d.flags.includes(f) ? d.flags.filter(x => x !== f) : [...d.flags, f],
    }));
  }

  /** Maps the draft onto the exact shape FormsController expects. */
  /**
   * "I am not sure" — a first-class answer, not a gap.
   *
   * Step 2 already recorded WHERE it hurts, with severity and quality. Step 3
   * asks WHAT the condition is, and a client who cannot pick between
   * Spondylosis and Radiculopathy is not failing to fill in a form: they are
   * being asked to self-diagnose by a system that says, on the same screen,
   * that it does not diagnose. B75 puts a number on it - "Others" is 28 of 137
   * real archived records, tied with Lower Back Pain as the commonest entry.
   *
   * Recorded as OTHER with the words "Not stated" rather than left null, so the
   * record can tell "they could not say" from "we never asked". Null would also
   * be refused by the server, and correctly: a chief complaint of nothing is
   * what the contraindication filter has nothing to check against.
   */
  setUnsure(): void {
    this.draft.update(d => ({
      ...d,
      complaints: d.complaints.includes('OTHER') ? d.complaints : [...d.complaints, 'OTHER'],
      mainComplaint: 'OTHER',
      mainComplaintOther: d.mainComplaintOther?.trim() || 'Not stated',
    }));
  }

  toFormsModel(branchId: string | null): FormsModel {
    const d = this.draft();
    const points: PatientIntakeModel[] = d.points.map(p => ({
      anatomicalRegion: p.region,
      side: p.side,
      bodyView: p.view,
      coordinateX: Math.round(p.x * COORD_SCALE),
      coordinateY: Math.round(p.y * COORD_SCALE),
      painScoreBefore: p.score,
      complaintType: d.mainComplaint,
      // painScoreAfter is never sent from here - it does not exist yet. The
      // client records it after the visit, through the outcome route, once
      // the front desk has marked the visit completed.
    }));

    return {
      branchId,
      intent: d.intent ?? 'PAIN',
      // A leisure assessment has no chief complaint. mainComplaint is nullable
      // for exactly this reason — see paper-deltas §H6.
      mainComplaint: d.intent === 'LEISURE' ? null : d.mainComplaint,
      mainComplaintOther: d.mainComplaint === 'OTHER' ? d.mainComplaintOther : null,
      mainComplaintDuration: d.duration,
      hadIllness: d.hadIllness,
      medicalHistory: d.hadIllness ? (d.illnessDetail || null) : null,
      hasTherapy: d.hasTherapy,
      therapyDetail: d.hasTherapy
        ? [d.therapyKind, d.therapyWhen].filter(Boolean).join(', ') || null
        : null,
      status: 'SUBMITTED',
      // H9 / B44 - real columns at last. These used to be flattened into a
      // sentence inside remarks, which meant nobody could query them, no
      // protocol rule could key on them, and the assistant was told only that
      // "something was noted".
      safetyFlags: d.flags,
      pressurePreference: d.pressure,
      therapistPreference: d.therapist,
      // remarks is now what it always should have been: free text, and empty
      // unless a human actually wrote something.
      remarks: null,
      painPoints: points,
    };
  }
}

/*
 * buildRemarks() lived here.
 *
 * It flattened the safety checklist and the pressure preference into a sentence
 * because neither had a column: "Flagged: Pregnant, Taking blood thinners |
 * Preferred pressure: Firm". Both have real columns as of 2026-08-30 (H9 / B44),
 * so the stopgap is gone and `remarks` is free text again.
 *
 * Kept as a note rather than deleted silently: assessments written before that
 * date still carry those sentences in remarks, and anyone reading old records
 * needs to know why.
 */

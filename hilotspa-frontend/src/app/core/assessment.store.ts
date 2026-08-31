import { Injectable, computed, inject, signal } from '@angular/core';
import {
  AnatomicalRegion, AssessmentIntent, BodyView, ComplaintType,
  FormsModel, PatientIntakeModel, PressurePreference, SafetyFlag, Sex,
} from './models';
import { ProfileStore } from './profile.store';
import { Side } from './body-hotspots';

/** One marker on the body map, before it becomes a PatientIntakeModel. */
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
  toFormsModel(branchId: string | null): FormsModel {
    const d = this.draft();
    const points: PatientIntakeModel[] = d.points.map(p => ({
      anatomicalRegion: p.region,
      side: p.side,
      bodyView: p.view,
      coordinateX: Math.round(p.x * 10),   // 0-1000, resolution-independent
      coordinateY: Math.round(p.y * 10),
      painScoreBefore: p.score,
      complaintType: d.mainComplaint,
      // painScoreAfter is never sent from here — staff write it on S4.
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

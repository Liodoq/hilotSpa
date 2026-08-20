import { Injectable, computed, signal } from '@angular/core';
import {
  AnatomicalRegion, AssessmentIntent, BodyView, ComplaintType,
  FormsModel, PatientIntakeModel,
} from './models';

/** One marker on the body map, before it becomes a PatientIntakeModel. */
export interface PainPoint {
  key: string;             // client-side only, for tracking in @for
  view: BodyView;
  x: number;               // 0-100, percentage of the figure's width
  y: number;               // 0-100, percentage of the figure's height
  region: AnatomicalRegion;
  score: number;           // 1-10
  qualities: string[];     // Pain / Stiff / Weak / Numb, from the paper form
}

export interface AssessmentDraft {
  intent: AssessmentIntent | null;
  birthDate: string | null;
  sex: string | null;
  civilStatus: string | null;
  occupation: string | null;
  height: number | null;
  weight: number | null;
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
  flags: string[];
  pressure: string | null;
  consented: boolean;
}

const EMPTY: AssessmentDraft = {
  intent: null, birthDate: null, sex: null, civilStatus: null, occupation: null,
  height: null, weight: null, points: [], complaints: [], mainComplaint: null,
  mainComplaintOther: null, duration: null, hadIllness: null, illnessDetail: '',
  hasTherapy: null, therapyKind: '', therapyWhen: '', flags: [], pressure: null,
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
  readonly draft = signal<AssessmentDraft>(structuredClone(EMPTY));

  readonly isLeisure = computed(() => this.draft().intent === 'LEISURE');
  readonly pointCount = computed(() => this.draft().points.length);

  patch(part: Partial<AssessmentDraft>): void {
    this.draft.update(d => ({ ...d, ...part }));
  }

  reset(): void { this.draft.set(structuredClone(EMPTY)); }

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
      // if the chief complaint was just unticked, it can no longer be chief
      const mainComplaint = on && d.mainComplaint === c ? null : d.mainComplaint;
      return { ...d, complaints, mainComplaint };
    });
  }

  toggleFlag(f: string): void {
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
      bodyView: p.view,
      coordinateX: Math.round(p.x * 10),   // 0-1000, resolution-independent
      coordinateY: Math.round(p.y * 10),
      painScore: p.score,
      complaintType: d.mainComplaint,
    }));

    return {
      branchId,
      intent: d.intent ?? 'PAIN',
      // A leisure assessment has no chief complaint. mainComplaint is nullable
      // for exactly this reason — see paper-deltas §H6.
      mainComplaint: d.intent === 'LEISURE' ? null : d.mainComplaint,
      mainComplaintOther: d.mainComplaint === 'OTHER' ? d.mainComplaintOther : null,
      mainComplaintDuration: d.duration,
      hasTherapy: d.hasTherapy === true,
      status: 'SUBMITTED',
      remarks: buildRemarks(d),
      painPoints: points,
    };
  }
}

/**
 * TEMPORARY. Until H1/H2 land (occupation, medicalHistory, therapyDetail),
 * these answers have nowhere structured to go, so they are packed into
 * `remarks` rather than silently dropped. Delete this the moment those
 * columns exist — a text blob is not queryable and cannot be shown back.
 */
function buildRemarks(d: AssessmentDraft): string {
  const bits: string[] = [];
  if (d.occupation) bits.push(`Occupation: ${d.occupation}`);
  if (d.hadIllness) bits.push(`Illness/injury: ${d.illnessDetail || 'yes, no detail given'}`);
  if (d.hasTherapy) bits.push(`Therapy before: ${d.therapyKind || 'unspecified'}, ${d.therapyWhen || 'date unknown'}`);
  if (d.flags.length) bits.push(`Flagged: ${d.flags.join(', ')}`);
  if (d.pressure) bits.push(`Preferred pressure: ${d.pressure}`);
  return bits.join(' | ');
}

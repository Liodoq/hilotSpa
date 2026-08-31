/* Mirrors the backend model package. Keep names identical to the Java DTOs —
   Jackson maps by property name, so a rename here is a silent 500 there. */

export type Role = 'CUSTOMER' | 'STAFF' | 'ADMIN';
export type AssessmentIntent = 'PAIN' | 'LEISURE';
export type BodyView = 'FRONT' | 'BACK';
export type Side = 'LEFT' | 'RIGHT' | 'CENTRE';

export interface AuthResponse {
  token: string;
  expiresInSeconds: number;
  userId: string;
  email: string;
  fullName: string;
  role: Role;
  branchId: string | null;
  /** Set for branch staff. The back-office sidebar labels itself from this. */
  branchName: string | null;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  middleName?: string;
  contact: string;
  address: string;
  email: string;
  password: string;
}

export interface LoginRequest { email: string; password: string; }

export interface Branch { id: string; name?: string; branchName?: string; address?: string; }

/* --- the 24 conditions + Others, verbatim from Appendix A --- */
export type ComplaintType =
  | 'NECK_PAIN' | 'SHOULDER_PAIN' | 'UPPER_BACK_PAIN' | 'LOWER_BACK_PAIN'
  | 'ELBOW_PAIN' | 'WRIST_PAIN' | 'HIP_JOINT_PAIN' | 'KNEE_PAIN' | 'ANKLE_PAIN'
  | 'STIFF_NECK' | 'FROZEN_SHOULDER' | 'SCIATICA' | 'SCOLIOSIS' | 'OSTEOARTHRITIS'
  | 'SPONDYLOSIS' | 'DISC_BULGE' | 'SLIP_DISC' | 'DDD' | 'DISC_DESICCATION'
  | 'STENOSIS' | 'PLANTAR_FASCIITIS' | 'RADICULOPATHY' | 'CTS' | 'TMJ_DISORDER'
  | 'OTHER';

export const COMPLAINTS: ReadonlyArray<{ value: ComplaintType; label: string }> = [
  { value: 'NECK_PAIN',         label: 'Neck Pain' },
  { value: 'SHOULDER_PAIN',     label: 'Shoulder Pain' },
  { value: 'UPPER_BACK_PAIN',   label: 'Upper Back Pain' },
  { value: 'LOWER_BACK_PAIN',   label: 'Lower Back Pain' },
  { value: 'ELBOW_PAIN',        label: 'Elbow Pain' },
  { value: 'WRIST_PAIN',        label: 'Wrist Pain' },
  { value: 'HIP_JOINT_PAIN',    label: 'Hip Joint Pain' },
  { value: 'KNEE_PAIN',         label: 'Knee Pain' },
  { value: 'ANKLE_PAIN',        label: 'Ankle Pain' },
  { value: 'STIFF_NECK',        label: 'Stiff Neck' },
  { value: 'FROZEN_SHOULDER',   label: 'Frozen Shoulder' },
  { value: 'SCIATICA',          label: 'Sciatica' },
  { value: 'SCOLIOSIS',         label: 'Scoliosis' },
  { value: 'OSTEOARTHRITIS',    label: 'Osteoarthritis' },
  { value: 'SPONDYLOSIS',       label: 'Spondylosis' },
  { value: 'DISC_BULGE',        label: 'Disc Bulge' },
  { value: 'SLIP_DISC',         label: 'Slip Disc' },
  { value: 'DDD',               label: 'DDD' },
  { value: 'DISC_DESICCATION',  label: 'Disc Desiccation' },
  { value: 'STENOSIS',          label: 'Stenosis' },
  { value: 'PLANTAR_FASCIITIS', label: 'Plantar Fasciitis' },
  { value: 'RADICULOPATHY',     label: 'Radiculopathy' },
  { value: 'CTS',               label: 'CTS' },
  { value: 'TMJ_DISORDER',      label: 'TMJ Disorder' },
];

/* --- the eleven regions from the findings table on the paper form ---
   Sent as SCREAMING_SNAKE so they already match the Java enum constants
   that H4 will introduce. The backend still types this as String today. */
export type AnatomicalRegion =
  | 'CERVICAL' | 'SHOULDER' | 'ELBOW' | 'WRIST' | 'THORACIC' | 'MID_BACK'
  | 'LUMBAR' | 'SI_JOINT' | 'HIP_JOINT' | 'KNEE' | 'ANKLE';

export const REGIONS: ReadonlyArray<{ value: AnatomicalRegion; label: string }> = [
  { value: 'CERVICAL',  label: 'Cervical (neck)' },
  { value: 'SHOULDER',  label: 'Shoulder' },
  { value: 'ELBOW',     label: 'Elbow' },
  { value: 'WRIST',     label: 'Wrist' },
  { value: 'THORACIC',  label: 'Thoracic (upper back)' },
  { value: 'MID_BACK',  label: 'Mid Back' },
  { value: 'LUMBAR',    label: 'Lumbar (lower back)' },
  { value: 'SI_JOINT',  label: 'S.I. Joint' },
  { value: 'HIP_JOINT', label: 'Hip Joint' },
  { value: 'KNEE',      label: 'Knee' },
  { value: 'ANKLE',     label: 'Ankle' },
];

/** What the backend receives for one pain marker. */
export interface PatientIntakeModel {
  id?: string;
  anatomicalRegion: AnatomicalRegion;
  side: Side | null;
  bodyView: BodyView;
  coordinateX: number;
  coordinateY: number;
  /** recorded by the client at the start */
  painScoreBefore: number;
  /** written by staff at the end of the session — never sent from here */
  painScoreAfter?: number | null;
  complaintType: ComplaintType | null;
}

/** What the backend receives for the whole assessment. Forms is the aggregate
 *  root, so this is posted exactly once, from C7. */
export interface FormsModel {
  id?: string;
  userId?: string | null;
  branchId?: string | null;
  intent: AssessmentIntent;
  mainComplaint: ComplaintType | null;
  mainComplaintOther?: string | null;
  mainComplaintDuration?: string | null;
  hadIllness: boolean | null;
  medicalHistory?: string | null;
  hasTherapy: boolean | null;
  therapyDetail?: string | null;
  status?: string | null;
  remarks?: string | null;
  createdAt?: string;
  painPoints: PatientIntakeModel[];
  /** Set instead of userId when staff took the assessment for a walk-in (B85). */
  walkInName?: string | null;
  /** H9 - the safety checklist, by enum name. Used to be prose inside remarks. */
  safetyFlags?: SafetyFlag[];
  /** H9 - LIGHT / MEDIUM / FIRM. Used to be prose inside remarks. */
  pressurePreference?: PressurePreference | null;
  /** FEMALE, MALE, or null for no preference. */
  therapistPreference?: Sex | null;
  /** The visit this assessment led to, if any. Null until something is booked. */
  visit?: FormVisit | null;
}

/**
 * The appointment an assessment produced, flattened for the client's own record.
 *
 * Read-only by design: no price, no payment status, nothing actionable. It
 * exists so a finished session can name the treatment, the practitioner and the
 * room that produced its pain scores instead of three em-dashes (B92).
 */
/**
 * The safety checklist, as the server stores it (paper-deltas H9).
 *
 * Enum names, not the sentences the client read. The label lives in
 * SAFETY_FLAGS below so the wording can be corrected without orphaning every
 * record that used the old sentence - the same reason ComplaintType carries a
 * displayName (B27).
 */
export type SafetyFlag =
  | 'PREGNANT' | 'HIGH_BLOOD_PRESSURE' | 'HEART_CONDITION' | 'DIABETES'
  | 'VARICOSE_VEINS' | 'RECENT_FRACTURE_OR_SURGERY' | 'OPEN_WOUND_OR_SKIN_INFECTION'
  | 'CANCER_OR_UNDER_TREATMENT' | 'BLOOD_THINNERS' | 'OSTEOPOROSIS';

export type PressurePreference = 'LIGHT' | 'MEDIUM' | 'FIRM';

/**
 * A therapist's sex, and the client's preference about it.
 *
 * There is no 'ANY'. A client with no preference has null — an absent
 * preference is absent, not a third kind of choice, and storing it as one would
 * make "never asked" indistinguishable from "said it does not matter".
 */
export type Sex = 'FEMALE' | 'MALE';

export const THERAPIST_PREFERENCES: { value: Sex | null; label: string; note: string }[] = [
  { value: null,     label: 'No preference', note: 'Any of our therapists' },
  { value: 'FEMALE', label: 'A woman',       note: 'Female therapist only' },
  { value: 'MALE',   label: 'A man',         note: 'Male therapist only' },
];

/** Must stay word for word in step with SafetyFlag.java. */
export const SAFETY_FLAGS: { value: SafetyFlag; label: string }[] = [
  { value: 'PREGNANT',                     label: 'Pregnant' },
  { value: 'HIGH_BLOOD_PRESSURE',          label: 'High blood pressure' },
  { value: 'HEART_CONDITION',              label: 'Heart condition' },
  { value: 'DIABETES',                     label: 'Diabetes' },
  { value: 'VARICOSE_VEINS',               label: 'Varicose veins' },
  { value: 'RECENT_FRACTURE_OR_SURGERY',   label: 'Fracture or surgery in the last 6 weeks' },
  { value: 'OPEN_WOUND_OR_SKIN_INFECTION', label: 'Open wound or skin infection' },
  { value: 'CANCER_OR_UNDER_TREATMENT',    label: 'Cancer, or under treatment' },
  { value: 'BLOOD_THINNERS',               label: 'Taking blood thinners' },
  { value: 'OSTEOPOROSIS',                 label: 'Osteoporosis' },
];

export const PRESSURES: { value: PressurePreference; label: string }[] = [
  { value: 'LIGHT',  label: 'Light' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'FIRM',   label: 'Firm' },
];

/** The sentence a client read, for a stored flag. */
export function safetyFlagLabel(v: SafetyFlag | string): string {
  return SAFETY_FLAGS.find(f => f.value === v)?.label ?? String(v);
}

export interface FormVisit {
  appointmentId: string;
  serviceName: string;
  durationMinutes: number;
  label: string;
  start: string;
  therapist: string;
  room: string;
  branch: string;
  status: string;
}

/** What POST /api/v1/assistant/recommend/{formId} returns. */
export interface AssistantRecommendation {
  serviceId: string;
  name: string;
  rank: number;
  reason: string;
  /** The spa sells the same treatment at two lengths — the name alone is
   *  ambiguous, so these come back from the database row with it. */
  durationMinutes: number;
  price: number;
}

/** One bookable time the assistant was allowed to name. Tapping one books it. */
export interface AssistantSlot {
  slotId: string;
  serviceId: string;
  serviceName: string;
  label: string;
  durationMinutes: number;
  price: number;
  start: string;
  /** "Today" / "Tomorrow" / "Thu 27 Aug" — rendered by the server, in the
   *  spa's timezone, so a wrong device clock cannot rename a day. */
  dayLabel: string;
  timeLabel: string;
}

export interface AssistantResponse {
  formId: string;
  /** OK = the model answered | FALLBACK = protocol table | REFER = see the practitioner */
  status: 'OK' | 'FALLBACK' | 'REFER';
  recommendations: AssistantRecommendation[];
  modelUsed: string;
  /** services the model named that Java had not approved, and that were dropped */
  rejectedCount: number;
  /** survived the ServiceProtocol filter */
  allowedCount: number;
  /** removed as CONTRAINDICATED before the model was asked */
  excludedCount: number;
  latencyMs: number;
  note: string | null;
}

export interface BookingModel {
  id: string;
  serviceId: string;
  serviceName: string;
  start: string;
  end: string;
  label: string;
  durationMinutes: number;
  price: number;
  therapist: string;
  room: string;
  branch: string;
  status: string;
  paymentStatus: string;
  source: string;
  /** when the booking was made, not when the visit is */
  bookedAt: string;
}

export interface AssistantChatResponse {
  reply: string;
  /** OK | BOOKED | CONFLICT | REJECTED | FALLBACK | REFER */
  status: string;
  /** present only when status is BOOKED */
  booking: BookingModel | null;
  /** dev profile only — why a fallback happened, so you need not read logs */
  debug?: string | null;
  /** Every time the assistant could have named, so the client can tap one
   *  instead of having to word a confirmation the model will parse. */
  slots?: AssistantSlot[] | null;
}

export interface AccountMe {
  id: string;
  firstName: string | null;
  middleName: string | null;
  lastName: string | null;
  contact: string | null;
  address: string | null;
  email: string | null;
  role: string | null;
}

export interface DemographicsModel {
  id?: string;
  usersid?: string | null;
  age: number | null;
  sex: string | null;
  occupation: string | null;
  status: string | null;
  height: number | null;
  weight: number | null;
  birthDate: string | null;
}

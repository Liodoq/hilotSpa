/* Mirrors the backend model package. Keep names identical to the Java DTOs —
   Jackson maps by property name, so a rename here is a silent 500 there. */

export type Role = 'CUSTOMER' | 'STAFF' | 'ADMIN';
export type AssessmentIntent = 'PAIN' | 'LEISURE';
export type BodyView = 'FRONT' | 'BACK';

export interface AuthResponse {
  token: string;
  expiresInSeconds: number;
  userId: string;
  email: string;
  fullName: string;
  role: Role;
  branchId: string | null;
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
  anatomicalRegion: string;
  bodyView: string;
  coordinateX: number;
  coordinateY: number;
  painScore: number;
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
  hasTherapy: boolean;
  status?: string | null;
  remarks?: string | null;
  createdAt?: string;
  painPoints: PatientIntakeModel[];
}

export interface DemographicsModel {
  id?: string;
  usersid?: string | null;
  age: number | null;
  sex: string | null;
  status: string | null;
  height: number | null;
  weight: number | null;
  birthDate: string | null;
}

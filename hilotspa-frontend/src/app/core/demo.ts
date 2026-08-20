/* ===========================================================================
   PLACEHOLDER DATA — Sprints 2 and 3 have no backend yet.
   Every constant below is replaced by a real call once the matching service
   exists. The TODO on each block names the endpoint that will replace it, so
   nothing here can quietly become permanent.
   =========================================================================== */
import { AnatomicalRegion, BodyView } from './models';

export interface DemoService {
  id: string; name: string; minutes: number; price: number;
  blurb: string; therapists: string; suitable: boolean; reason?: string; best?: boolean;
}
export interface DemoAppointment {
  ref: string; service: string; minutes: number; date: string; day: string; month: string;
  dayNum: string; time: string; therapist: string; room: string; branch: string;
  price: number; status: 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';
}
export interface DemoSlot {
  time: string; service: string; minutes: number; client: string; therapist: string | null;
  room: string; state: 'COMPLETED' | 'IN_SESSION' | 'UPCOMING'; walkIn: boolean;
}
export interface DemoQueueItem {
  id: string; name: string; arrived: string; waitedMin: number; service: string;
  hasAssessment: boolean; flag?: string;
}
export interface DemoStaff { id: string; name: string; initials: string; role: string;
  years: number; status: 'AVAILABLE' | 'ON_SESSION' | 'BREAK' | 'OFF_DUTY'; note: string; }
export interface DemoRoom { id: string; name: string; detail: string; state: 'FREE' | 'OCCUPIED' | 'CLOSED'; }
export interface DemoNode { id: string; branch: string; online: boolean; synced: string;
  bookings: number; collected: number; waiting: number; queued: number; }
export interface DemoAccount { name: string; email: string; role: string; branch: string; enabled: boolean; }
export interface DemoRule { service: string; condition: string; indicated: boolean; reason: string; }
export interface DemoAudit { time: string; who: string; whoRole: string; action: string;
  record: string; node: string; queued?: boolean; }
export interface DemoPoint { key: string; view: BodyView; x: number; y: number;
  region: AnatomicalRegion; score: number; qualities: string[]; }

/* TODO Sprint 2 — GET /api/v1/massages, filtered by ServiceProtocol */
export const SERVICES: DemoService[] = [
  { id: 'svc-back', name: 'Hilot sa Likod', minutes: 60, price: 750, suitable: true, best: true,
    blurb: 'Traditional back-focused hilot. Works along the lumbar and S.I. joint — the two areas you marked.',
    therapists: 'Marites, Josie' },
  { id: 'svc-full', name: 'Full Body Massage', minutes: 90, price: 900, suitable: true,
    blurb: 'Whole-body relaxation with light attention to the lower back.', therapists: '3 therapists' },
  { id: 'svc-foot', name: 'Foot Reflexology', minutes: 45, price: 550, suitable: true,
    blurb: 'Pressure-point work on the feet. Safe alongside back treatment.', therapists: '2 therapists' },
  { id: 'svc-deep', name: 'Deep Tissue Massage', minutes: 60, price: 850, suitable: false,
    blurb: 'Contraindicated with high blood pressure by the spa’s own protocol.',
    therapists: '', reason: 'High blood pressure' },
  { id: 'svc-vent', name: 'Ventosa (Cupping)', minutes: 45, price: 700, suitable: false,
    blurb: 'Contraindicated with high blood pressure by the spa’s own protocol.',
    therapists: '', reason: 'High blood pressure' },
];

/* TODO Sprint 2 — GET /api/v1/appointments?mine */
export const NEXT_VISIT: DemoAppointment = {
  ref: 'KWS-2608-0431', service: 'Hilot sa Likod', minutes: 60, date: '23 August 2026',
  day: 'Sunday', month: 'AUG', dayNum: '23', time: '2:00 PM – 3:00 PM',
  therapist: 'Marites Bonifacio', room: 'Room 2', branch: 'Bulan, Sorsogon',
  price: 750, status: 'CONFIRMED',
};

export const PAST_VISITS = [
  { date: '02 Aug 2026', service: 'Hilot sa Likod', complaint: 'Lower Back Pain', before: 8, after: 4 },
  { date: '12 Jul 2026', service: 'Ventosa', complaint: 'Stiff Neck', before: 6, after: 3 },
  { date: '28 Jun 2026', service: 'Full Body Massage', complaint: 'Here to relax', before: null, after: null },
];

/* TODO Sprint 1 — this comes from the customer's own last Forms record */
export const LAST_POINTS: DemoPoint[] = [
  { key: 'd1', view: 'FRONT', x: 63, y: 22, region: 'SHOULDER', score: 8, qualities: ['Pain'] },
  { key: 'd2', view: 'BACK',  x: 50, y: 47, region: 'LUMBAR',   score: 7, qualities: ['Pain', 'Stiff'] },
  { key: 'd3', view: 'FRONT', x: 78, y: 41, region: 'ELBOW',    score: 5, qualities: ['Stiff'] },
];

/* TODO Sprint 3 — GET /api/v1/appointments?branch={own}&date=today */
export const TODAY: DemoSlot[] = [
  { time: '1:00 PM', service: 'Ventosa', minutes: 45, client: 'Rosa Mendoza',
    therapist: 'Marites', room: 'Room 1', state: 'COMPLETED', walkIn: false },
  { time: '2:00 PM', service: 'Hilot sa Likod', minutes: 60, client: 'Ana Reyes',
    therapist: 'Marites', room: 'Room 2', state: 'IN_SESSION', walkIn: false },
  { time: '2:30 PM', service: 'Foot Reflexology', minutes: 45, client: 'Walk-in',
    therapist: 'Josie', room: 'Room 1', state: 'UPCOMING', walkIn: true },
  { time: '3:15 PM', service: 'Full Body Massage', minutes: 90, client: 'Delia Cruz',
    therapist: null, room: 'Room 2', state: 'UPCOMING', walkIn: false },
  { time: '5:00 PM', service: 'Hilot sa Likod', minutes: 60, client: 'Pedro Santos',
    therapist: 'Josie', room: 'Room 1', state: 'UPCOMING', walkIn: false },
];

export const QUEUE: DemoQueueItem[] = [
  { id: 'q1', name: 'Rosa Mendoza', arrived: '2:29 PM', waitedMin: 18,
    service: 'Foot Reflexology', hasAssessment: true },
  { id: 'q2', name: 'Pedro Santos', arrived: '2:35 PM', waitedMin: 12,
    service: 'Hilot sa Likod', hasAssessment: false },
  { id: 'q3', name: 'Delia Cruz', arrived: '2:41 PM', waitedMin: 6,
    service: 'Not yet decided', hasAssessment: true, flag: 'Pregnant — services limited' },
];

/* TODO Sprint 3 — GET /api/v1/therapists?branch={own} */
export const STAFF: DemoStaff[] = [
  { id: 't1', name: 'Marites Bonifacio', initials: 'MB', role: 'Bone setter', years: 8,
    status: 'ON_SESSION', note: 'Ana Reyes · Room 2 · until 3:00 PM' },
  { id: 't2', name: 'Josie Delgado', initials: 'JD', role: 'Massage therapist', years: 5,
    status: 'AVAILABLE', note: 'Free until the 2:30 PM walk-in' },
  { id: 't3', name: 'Nena Ramos', initials: 'NR', role: 'Massage therapist', years: 3,
    status: 'OFF_DUTY', note: 'Rest day · back Friday' },
];
export const ROOMS: DemoRoom[] = [
  { id: 'r1', name: 'Room 1', detail: 'Two beds · ground floor', state: 'FREE' },
  { id: 'r2', name: 'Room 2', detail: 'One bed · private', state: 'OCCUPIED' },
];
export const STAFF_STATUSES = ['AVAILABLE', 'ON_SESSION', 'BREAK', 'OFF_DUTY'] as const;
export const ROOM_STATES = ['FREE', 'OCCUPIED', 'CLOSED'] as const;

/* TODO Sprint 3 — the aggregation endpoint, once sync exists */
export const NODES: DemoNode[] = [
  { id: 'bulan-01', branch: 'Bulan', online: true, synced: '40 seconds ago',
    bookings: 9, collected: 6150, waiting: 3, queued: 0 },
  { id: 'sorsogon-01', branch: 'Sorsogon City', online: false, synced: '14 minutes ago',
    bookings: 12, collected: 8150, waiting: 0, queued: 4 },
];

export const TOP_COMPLAINTS = [
  { label: 'Lower Back Pain', n: 31, pct: 78 },
  { label: 'Stiff Neck', n: 22, pct: 55 },
  { label: 'Shoulder Pain', n: 17, pct: 42 },
  { label: 'Sciatica', n: 11, pct: 28 },
];

/* TODO Sprint 3 — GET /api/v1/users */
export const ACCOUNTS: DemoAccount[] = [
  { name: 'Raymund Hilotin', email: 'admin@kneadwellness.ph', role: 'ADMIN', branch: '— all branches', enabled: true },
  { name: 'Josie Delgado', email: 'staff.bulan@kneadwellness.ph', role: 'STAFF', branch: 'Bulan', enabled: true },
  { name: 'Chobel Hilotin', email: 'staff.sorsogon@kneadwellness.ph', role: 'STAFF', branch: 'Sorsogon City', enabled: true },
  { name: 'Ana Reyes', email: 'ana@email.com', role: 'CUSTOMER', branch: '—', enabled: true },
  { name: 'Ben Torres', email: 'ben@email.com', role: 'CUSTOMER', branch: '—', enabled: false },
];

/* TODO Sprint 2 — GET /api/v1/service-protocol */
export const RULES: DemoRule[] = [
  { service: 'Hilot sa Likod', condition: 'Lower Back Pain', indicated: true, reason: 'Direct lumbar work' },
  { service: 'Hilot sa Likod', condition: 'Sciatica', indicated: true, reason: 'With light pressure only' },
  { service: 'Deep Tissue Massage', condition: 'High blood pressure', indicated: false, reason: 'Pressure response' },
  { service: 'Ventosa (Cupping)', condition: 'High blood pressure', indicated: false, reason: 'Bruising and circulation' },
  { service: 'Ventosa (Cupping)', condition: 'Taking blood thinners', indicated: false, reason: 'Bleeding risk' },
  { service: 'Deep Tissue Massage', condition: 'Pregnant', indicated: false, reason: 'Any trimester' },
  { service: 'Full Body Massage', condition: 'Fracture in the last 6 weeks', indicated: false, reason: 'Until cleared' },
];

/* TODO Sprint 3 — GET /api/v1/audit-log */
export const AUDIT: DemoAudit[] = [
  { time: '2:42 PM', who: 'Ana Reyes', whoRole: 'CUSTOMER', action: 'BOOKING_CREATED',
    record: 'KWS-2608-0431 · Hilot sa Likod', node: 'bulan-01' },
  { time: '2:38 PM', who: 'Ana Reyes', whoRole: 'CUSTOMER', action: 'ASSESSMENT_SUBMITTED',
    record: 'form 8c41… · 3 pain points', node: 'bulan-01' },
  { time: '2:15 PM', who: 'Josie Delgado', whoRole: 'STAFF', action: 'BOOKING_CANCELLED',
    record: 'KWS-2608-0428 · reason: client no-show', node: 'bulan-01' },
  { time: '1:50 PM', who: 'Josie Delgado', whoRole: 'STAFF', action: 'THERAPIST_STATUS',
    record: 'Nena Ramos · AVAILABLE → OFF_DUTY', node: 'bulan-01' },
  { time: '11:04 AM', who: 'Raymund Hilotin', whoRole: 'ADMIN', action: 'PROTOCOL_EDITED',
    record: 'Ventosa × blood thinners → CONTRAINDICATED', node: 'global' },
  { time: '10:22 AM', who: 'Raymund Hilotin', whoRole: 'ADMIN', action: 'ROLE_ASSIGNED',
    record: 'chobel@… · CUSTOMER → STAFF (Sorsogon City)', node: 'global' },
  { time: '9:47 AM', who: 'Chobel Hilotin', whoRole: 'STAFF', action: 'BOOKING_CREATED',
    record: 'SC-2608-0117 · recorded offline', node: 'sorsogon-01', queued: true },
];

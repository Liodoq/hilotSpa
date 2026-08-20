import { AnatomicalRegion, BodyView } from './models';

/**
 * Guesses which of the eleven regions a tap landed on.
 *
 * This is a HEURISTIC, deliberately. The client corrects it with a dropdown on
 * C4, and the value they confirm is what gets stored — the system never quietly
 * decides an anatomical fact on its own. x and y are 0-100.
 */
export function guessRegion(view: BodyView, x: number, y: number): AnatomicalRegion {
  const isArm = x < 32 || x > 68;

  if (y < 16) return 'CERVICAL';

  if (isArm) {
    if (y < 33) return 'SHOULDER';
    if (y < 46) return 'ELBOW';
    if (y < 56) return 'WRIST';
  }

  if (y < 25) return 'SHOULDER';
  if (y < 35) return 'THORACIC';
  if (y < 41) return 'MID_BACK';
  if (y < 47) return 'LUMBAR';
  if (y < 52) return view === 'BACK' ? 'SI_JOINT' : 'HIP_JOINT';
  if (y < 62) return 'HIP_JOINT';
  if (y < 80) return 'KNEE';
  return 'ANKLE';
}

/** forest / gold / terracotta by severity. Colour is never the only signal —
 *  every marker also carries its number (WCAG 1.4.1). */
export function severityClass(score: number): 's-low' | 's-mid' | 's-high' {
  if (score <= 3) return 's-low';
  if (score <= 6) return 's-mid';
  return 's-high';
}

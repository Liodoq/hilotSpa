import { AnatomicalRegion, BodyView, Side } from './models';

export type { Side };

export interface Hotspot {
  id: string;
  view: BodyView;
  x: number;          // percent of the figure's width
  y: number;          // percent of its height
  region: AnatomicalRegion;
  side: Side;
  /**
   * Where this actually is, in the words a client would use.
   *
   * The region alone is ambiguous across the two views: "Knee, left" is both
   * the kneecap and the hollow behind the knee, and a client choosing between
   * two identical-looking dots had nothing to choose ON. The practitioner
   * reading the marked list afterwards had the same problem.
   */
  where: string;
}

/**
 * Fixed anatomical positions, instead of free coordinates.
 *
 * Free tapping let people mark empty space beside the torso, and "x=78, y=41"
 * is not a clinical fact anyway — two clients pointing at the same shoulder
 * produced two different numbers, so nothing could be aggregated across
 * records. Snapping to a known point means the coordinate and the region can
 * never disagree, and the counts on the admin dashboard actually mean something.
 *
 * MIRRORING: the back view is the body seen from behind, so the client's LEFT
 * appears on the viewer's RIGHT. `side` is always the CLIENT's own side — which
 * is what the L/R columns on the paper intake form record.
 */
export const HOTSPOTS: readonly Hotspot[] = [
  // ---------------- FRONT ----------------
  // No thoracic spot here. The thoracic spine is a POSTERIOR structure - there
  // is none on the front of a person. A spot at chest height labelled THORACIC
  // recorded anyone tapping their sternum as mid-back pain, indistinguishable
  // from somebody who had marked the back correctly. Removed 2026-09-21; the
  // count in the paper moves from 30 to 29. See paper-deltas.
  { id: 'f-cerv',   view: 'FRONT', x: 50, y: 14, region: 'CERVICAL',  side: 'CENTRE', where: 'front of the neck' },
  { id: 'f-sh-r',   view: 'FRONT', x: 33, y: 19, region: 'SHOULDER',  side: 'RIGHT',  where: 'front of the shoulder' },
  { id: 'f-sh-l',   view: 'FRONT', x: 67, y: 19, region: 'SHOULDER',  side: 'LEFT',   where: 'front of the shoulder' },
  { id: 'f-el-r',   view: 'FRONT', x: 24, y: 33, region: 'ELBOW',     side: 'RIGHT',  where: 'inside of the elbow' },
  { id: 'f-el-l',   view: 'FRONT', x: 76, y: 33, region: 'ELBOW',     side: 'LEFT',   where: 'inside of the elbow' },
  { id: 'f-hip-r',  view: 'FRONT', x: 42, y: 42, region: 'HIP_JOINT', side: 'RIGHT',  where: 'front of the hip' },
  { id: 'f-hip-l',  view: 'FRONT', x: 58, y: 42, region: 'HIP_JOINT', side: 'LEFT',   where: 'front of the hip' },
  { id: 'f-wr-r',   view: 'FRONT', x: 25, y: 48, region: 'WRIST',     side: 'RIGHT',  where: 'palm side of the wrist' },
  { id: 'f-wr-l',   view: 'FRONT', x: 75, y: 48, region: 'WRIST',     side: 'LEFT',   where: 'palm side of the wrist' },
  { id: 'f-kn-r',   view: 'FRONT', x: 43, y: 66, region: 'KNEE',      side: 'RIGHT',  where: 'kneecap' },
  { id: 'f-kn-l',   view: 'FRONT', x: 57, y: 66, region: 'KNEE',      side: 'LEFT',   where: 'kneecap' },
  { id: 'f-an-r',   view: 'FRONT', x: 43, y: 87, region: 'ANKLE',     side: 'RIGHT',  where: 'front of the ankle' },
  { id: 'f-an-l',   view: 'FRONT', x: 57, y: 87, region: 'ANKLE',     side: 'LEFT',   where: 'front of the ankle' },

  // ---------------- BACK (sides mirrored) ----------------
  { id: 'b-cerv',   view: 'BACK',  x: 50, y: 14, region: 'CERVICAL',  side: 'CENTRE', where: 'back of the neck' },
  { id: 'b-sh-l',   view: 'BACK',  x: 33, y: 19, region: 'SHOULDER',  side: 'LEFT',   where: 'back of the shoulder' },
  { id: 'b-sh-r',   view: 'BACK',  x: 67, y: 19, region: 'SHOULDER',  side: 'RIGHT',  where: 'back of the shoulder' },
  { id: 'b-thor',   view: 'BACK',  x: 50, y: 24, region: 'THORACIC',  side: 'CENTRE', where: 'upper back' },
  { id: 'b-el-l',   view: 'BACK',  x: 24, y: 33, region: 'ELBOW',     side: 'LEFT',   where: 'point of the elbow' },
  { id: 'b-el-r',   view: 'BACK',  x: 76, y: 33, region: 'ELBOW',     side: 'RIGHT',  where: 'point of the elbow' },
  { id: 'b-mid',    view: 'BACK',  x: 50, y: 31, region: 'MID_BACK',  side: 'CENTRE', where: 'middle of the back' },
  { id: 'b-lumb',   view: 'BACK',  x: 50, y: 38, region: 'LUMBAR',    side: 'CENTRE', where: 'lower back' },
  { id: 'b-si-l',   view: 'BACK',  x: 44, y: 44, region: 'SI_JOINT',  side: 'LEFT',   where: 'base of the spine' },
  { id: 'b-si-r',   view: 'BACK',  x: 56, y: 44, region: 'SI_JOINT',  side: 'RIGHT',  where: 'base of the spine' },
  { id: 'b-wr-l',   view: 'BACK',  x: 25, y: 48, region: 'WRIST',     side: 'LEFT',   where: 'back of the wrist' },
  { id: 'b-wr-r',   view: 'BACK',  x: 75, y: 48, region: 'WRIST',     side: 'RIGHT',  where: 'back of the wrist' },
  { id: 'b-kn-l',   view: 'BACK',  x: 43, y: 66, region: 'KNEE',      side: 'LEFT',   where: 'behind the knee' },
  { id: 'b-kn-r',   view: 'BACK',  x: 57, y: 66, region: 'KNEE',      side: 'RIGHT',  where: 'behind the knee' },
  { id: 'b-an-l',   view: 'BACK',  x: 43, y: 87, region: 'ANKLE',     side: 'LEFT',   where: 'back of the ankle' },
  { id: 'b-an-r',   view: 'BACK',  x: 57, y: 87, region: 'ANKLE',     side: 'RIGHT',  where: 'back of the ankle' },
];

/** Nearest hotspot on this view. Returns null past `maxDist` so a tap in empty
 *  space places nothing rather than snapping to something far away. */
export function nearestHotspot(view: BodyView, x: number, y: number, maxDist = 14): Hotspot | null {
  let best: Hotspot | null = null;
  let bestD = Infinity;
  for (const h of HOTSPOTS) {
    if (h.view !== view) continue;
    const d = Math.hypot(h.x - x, h.y - y);
    if (d < bestD) { bestD = d; best = h; }
  }
  return bestD <= maxDist ? best : null;
}

export function sideLabel(side: Side): string {
  return side === 'CENTRE' ? '' : side.toLowerCase();
}

/** The plain-language place, looked up from a stored point's hotspot id. */
export function whereOf(hotspotId: string | null | undefined): string {
  if (!hotspotId) { return ''; }
  return HOTSPOTS.find(h => h.id === hotspotId)?.where ?? '';
}

import { AnatomicalRegion, BodyView, Side } from './models';

export type { Side };

export interface Hotspot {
  id: string;
  view: BodyView;
  x: number;          // percent of the figure's width
  y: number;          // percent of its height
  region: AnatomicalRegion;
  side: Side;
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
  { id: 'f-cerv',   view: 'FRONT', x: 50, y: 14, region: 'CERVICAL',  side: 'CENTRE' },
  { id: 'f-sh-r',   view: 'FRONT', x: 33, y: 19, region: 'SHOULDER',  side: 'RIGHT' },
  { id: 'f-sh-l',   view: 'FRONT', x: 67, y: 19, region: 'SHOULDER',  side: 'LEFT'  },
  { id: 'f-thor',   view: 'FRONT', x: 50, y: 24, region: 'THORACIC',  side: 'CENTRE' },
  { id: 'f-el-r',   view: 'FRONT', x: 24, y: 33, region: 'ELBOW',     side: 'RIGHT' },
  { id: 'f-el-l',   view: 'FRONT', x: 76, y: 33, region: 'ELBOW',     side: 'LEFT'  },
  { id: 'f-hip-r',  view: 'FRONT', x: 42, y: 42, region: 'HIP_JOINT', side: 'RIGHT' },
  { id: 'f-hip-l',  view: 'FRONT', x: 58, y: 42, region: 'HIP_JOINT', side: 'LEFT'  },
  { id: 'f-wr-r',   view: 'FRONT', x: 25, y: 48, region: 'WRIST',     side: 'RIGHT' },
  { id: 'f-wr-l',   view: 'FRONT', x: 75, y: 48, region: 'WRIST',     side: 'LEFT'  },
  { id: 'f-kn-r',   view: 'FRONT', x: 43, y: 66, region: 'KNEE',      side: 'RIGHT' },
  { id: 'f-kn-l',   view: 'FRONT', x: 57, y: 66, region: 'KNEE',      side: 'LEFT'  },
  { id: 'f-an-r',   view: 'FRONT', x: 43, y: 87, region: 'ANKLE',     side: 'RIGHT' },
  { id: 'f-an-l',   view: 'FRONT', x: 57, y: 87, region: 'ANKLE',     side: 'LEFT'  },

  // ---------------- BACK (sides mirrored) ----------------
  { id: 'b-cerv',   view: 'BACK',  x: 50, y: 14, region: 'CERVICAL',  side: 'CENTRE' },
  { id: 'b-sh-l',   view: 'BACK',  x: 33, y: 19, region: 'SHOULDER',  side: 'LEFT'  },
  { id: 'b-sh-r',   view: 'BACK',  x: 67, y: 19, region: 'SHOULDER',  side: 'RIGHT' },
  { id: 'b-thor',   view: 'BACK',  x: 50, y: 24, region: 'THORACIC',  side: 'CENTRE' },
  { id: 'b-el-l',   view: 'BACK',  x: 24, y: 33, region: 'ELBOW',     side: 'LEFT'  },
  { id: 'b-el-r',   view: 'BACK',  x: 76, y: 33, region: 'ELBOW',     side: 'RIGHT' },
  { id: 'b-mid',    view: 'BACK',  x: 50, y: 31, region: 'MID_BACK',  side: 'CENTRE' },
  { id: 'b-lumb',   view: 'BACK',  x: 50, y: 38, region: 'LUMBAR',    side: 'CENTRE' },
  { id: 'b-si-l',   view: 'BACK',  x: 44, y: 44, region: 'SI_JOINT',  side: 'LEFT'  },
  { id: 'b-si-r',   view: 'BACK',  x: 56, y: 44, region: 'SI_JOINT',  side: 'RIGHT' },
  { id: 'b-wr-l',   view: 'BACK',  x: 25, y: 48, region: 'WRIST',     side: 'LEFT'  },
  { id: 'b-wr-r',   view: 'BACK',  x: 75, y: 48, region: 'WRIST',     side: 'RIGHT' },
  { id: 'b-kn-l',   view: 'BACK',  x: 43, y: 66, region: 'KNEE',      side: 'LEFT'  },
  { id: 'b-kn-r',   view: 'BACK',  x: 57, y: 66, region: 'KNEE',      side: 'RIGHT' },
  { id: 'b-an-l',   view: 'BACK',  x: 43, y: 87, region: 'ANKLE',     side: 'LEFT'  },
  { id: 'b-an-r',   view: 'BACK',  x: 57, y: 87, region: 'ANKLE',     side: 'RIGHT' },
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

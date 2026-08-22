import { Injectable, computed, inject, signal } from '@angular/core';
import { FormsApi } from './forms.api';
import { DemographicsModel } from './models';

/**
 * Demographics belong to the PERSON, not to the visit.
 *
 * They were originally step 2 of the assessment, which meant a returning client
 * re-typed their birth date and occupation before every booking. Age, sex,
 * civil status and occupation do not change between visits; height and weight
 * rarely do. So they live on the profile, are captured once, and the assessment
 * simply requires that they exist.
 *
 * The SERVER is the source of truth (POST/GET /api/v1/demographics/me, which
 * takes the user from the JWT). localStorage is kept only as a cache, for two
 * reasons: the route guards are synchronous and cannot await a fetch, and
 * NFR#1 expects the branch to keep working when the network does not.
 */
export interface Profile {
  birthDate: string | null;
  sex: string | null;
  civilStatus: string | null;
  occupation: string | null;
  height: number | null;
  weight: number | null;
}

const EMPTY: Profile = {
  birthDate: null, sex: null, civilStatus: null,
  occupation: null, height: null, weight: null,
};

/** Exported so logout can clear it. Importing a const creates no DI cycle. */
export const PROFILE_CACHE_KEY = 'hilotspa.profile';
const KEY = PROFILE_CACHE_KEY;

@Injectable({ providedIn: 'root' })
export class ProfileStore {
  private api = inject(FormsApi);

  /** Seeded from cache so guards can answer immediately, then refreshed. */
  readonly profile = signal<Profile>(restore());
  readonly loaded = signal(false);

  /** Height and weight stay optional — the paper form marks them so, and a
   *  client who would rather not say must not be blocked from booking. */
  readonly isComplete = computed(() => {
    const p = this.profile();
    return !!p.birthDate && !!p.sex && !!p.civilStatus;
  });

  readonly age = computed(() => ageFrom(this.profile().birthDate));

  /**
   * Pull the profile from the server. Safe to call when logged out — a 401 or
   * 204 simply leaves the cached value alone rather than wiping a profile the
   * client can see on screen.
   */
  async load(): Promise<void> {
    try {
      const dto = await this.api.myDemographics();
      if (dto) {
        this.profile.set(fromDto(dto));
        cache(this.profile());
      }
    } catch {
      // offline, or not logged in yet. The cache stands.
    } finally {
      this.loaded.set(true);
    }
  }

  patch(part: Partial<Profile>): void {
    this.profile.update(p => ({ ...p, ...part }));
  }

  /**
   * Writes to the server, then caches. Throws on failure so the caller can
   * tell the client — a profile that silently failed to save is worse than an
   * error, because the wizard would then let them through on a row that does
   * not exist.
   */
  async save(): Promise<void> {
    const saved = await this.api.saveMyDemographics(toDto(this.profile()));
    if (saved) this.profile.set(fromDto(saved));
    cache(this.profile());
  }

  clear(): void {
    localStorage.removeItem(KEY);
    this.profile.set({ ...EMPTY });
    this.loaded.set(false);
  }
}

// --- mapping -------------------------------------------------------------
// The API calls it `status`; the UI calls it civil status, because that is the
// wording on the paper form.

function toDto(p: Profile): DemographicsModel {
  return {
    age: ageFrom(p.birthDate),
    sex: p.sex,
    occupation: p.occupation,
    status: p.civilStatus,
    height: p.height,
    weight: p.weight,
    birthDate: p.birthDate,
  };
}

function fromDto(d: DemographicsModel): Profile {
  return {
    birthDate: d.birthDate ?? null,
    sex: d.sex ?? null,
    civilStatus: d.status ?? null,
    occupation: d.occupation ?? null,
    height: d.height ?? null,
    weight: d.weight ?? null,
  };
}

function ageFrom(birthDate: string | null): number | null {
  if (!birthDate) return null;
  const d = new Date(birthDate);
  if (Number.isNaN(d.getTime())) return null;
  const now = new Date();
  let a = now.getFullYear() - d.getFullYear();
  const m = now.getMonth() - d.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < d.getDate())) a--;
  return a >= 0 && a < 130 ? a : null;
}

function cache(p: Profile): void {
  try { localStorage.setItem(KEY, JSON.stringify(p)); } catch { /* private mode */ }
}

function restore(): Profile {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? { ...EMPTY, ...(JSON.parse(raw) as Profile) } : { ...EMPTY };
  } catch {
    return { ...EMPTY };
  }
}

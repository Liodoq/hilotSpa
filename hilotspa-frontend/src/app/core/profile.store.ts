import { Injectable, computed, signal } from '@angular/core';

/**
 * Demographics belong to the PERSON, not to the visit.
 *
 * They were originally step 2 of the assessment, which meant a returning client
 * re-typed their birth date and occupation before every booking. Age, sex,
 * civil status and occupation do not change between visits; height and weight
 * rarely do. So they live on the profile, are captured once, and the assessment
 * simply requires that they exist.
 *
 * TODO — persisted to localStorage until B43 is decided. /api/v1/demographics
 * is currently hasAnyRole(STAFF, ADMIN), so a customer cannot save their own.
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

const KEY = 'hilotspa.profile';

@Injectable({ providedIn: 'root' })
export class ProfileStore {
  readonly profile = signal<Profile>(restore());

  /** Height and weight stay optional — the paper form marks them so, and a
   *  client who would rather not say must not be blocked from booking. */
  readonly isComplete = computed(() => {
    const p = this.profile();
    return !!p.birthDate && !!p.sex && !!p.civilStatus;
  });

  readonly age = computed(() => {
    const b = this.profile().birthDate;
    if (!b) return null;
    const d = new Date(b);
    if (Number.isNaN(d.getTime())) return null;
    const now = new Date();
    let a = now.getFullYear() - d.getFullYear();
    const m = now.getMonth() - d.getMonth();
    if (m < 0 || (m === 0 && now.getDate() < d.getDate())) a--;
    return a >= 0 && a < 130 ? a : null;
  });

  patch(part: Partial<Profile>): void {
    this.profile.update(p => ({ ...p, ...part }));
  }

  save(): void {
    localStorage.setItem(KEY, JSON.stringify(this.profile()));
  }

  clear(): void {
    localStorage.removeItem(KEY);
    this.profile.set({ ...EMPTY });
  }
}

function restore(): Profile {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? { ...EMPTY, ...(JSON.parse(raw) as Profile) } : { ...EMPTY };
  } catch {
    return { ...EMPTY };
  }
}

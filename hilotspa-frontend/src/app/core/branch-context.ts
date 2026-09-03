import { Injectable, computed, signal } from '@angular/core';
import { Branch } from './models';

/**
 * Figure 3.3 — "Context Switching".
 *
 * The branch an ADMINISTRATOR has switched into. Null means the whole business.
 *
 * This exists only for administrators, and only because they have no branch of
 * their own. A STAFF account never reads this: their branch comes out of the
 * signed token on the server, so there is nothing here for them to set and
 * nothing a request could carry that would change it.
 *
 * Kept in sessionStorage, which is the distinction that matters.
 *
 * This was held in memory only, with the reasoning that persisting it would
 * mean "an administrator returns tomorrow still silently scoped to one branch,
 * and every figure they read would be a subset they did not ask for". That is
 * right about TOMORROW and wrong about ten seconds later: any reload dropped
 * the branch mid-task, and the screens then made no sense - the header still
 * read BRANCH VIEW while Record a walk-in said there was no branch to record
 * against. Losing state silently is its own kind of wrong answer.
 *
 * sessionStorage separates the two cases exactly: it survives a reload and a
 * navigation, and it is gone when the tab or the browser closes. So a refresh
 * keeps you where you were, and coming back tomorrow starts at all branches.
 *
 * Not a secret, and not a permission. The SERVER ignores branchId for any
 * caller who is not an administrator, so a tampered value buys nothing - which
 * is what BranchScopingTest covers.
 */
const KEY = 'hilotspa.branchctx';

function restore(): Branch | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    return raw ? JSON.parse(raw) as Branch : null;
  } catch {
    // Private mode, or a value from an older shape. Starting at all branches is
    // the safe default: it shows MORE than the administrator is entitled to see
    // nowhere, and hides nothing.
    return null;
  }
}

@Injectable({ providedIn: 'root' })
export class BranchContext {
  readonly branch = signal<Branch | null>(restore());
  readonly branchId = signal<string | null>(restore()?.id ?? null);

  /**
   * True when a branch has been entered.
   *
   * Deliberately does NOT read the signed-in role back out of the auth service.
   * That service clears this store on sign-out, so depending on it here is a
   * circular dependency in Angular's DI: neither service can be constructed and
   * the app renders an empty <app-root>. That is exactly what happened, and
   * `ngc` cannot catch it — it type-checks, it does not resolve injectors.
   *
   * Nothing is weakened by leaving it out. Only /admin routes set this store and
   * they sit behind roleGuard('ADMIN'); DashShell gates its own "visiting" state
   * on the real role; and the SERVER ignores branchId for any caller who is not
   * an administrator — which is the check that actually matters, and the one
   * BranchScopingTest covers.
   */
  readonly active = computed(() => this.branchId() !== null);

  readonly name = computed(() => {
    const b = this.branch();
    const full = b?.name ?? b?.branchName ?? '';
    const dash = full.lastIndexOf('-');
    return dash === -1 ? full : full.slice(dash + 1).trim();
  });

  enter(branch: Branch): void {
    this.branchId.set(branch.id);
    this.branch.set(branch);
    try { sessionStorage.setItem(KEY, JSON.stringify(branch)); } catch { /* private mode */ }
  }

  leave(): void {
    this.branchId.set(null);
    this.branch.set(null);
    try { sessionStorage.removeItem(KEY); } catch { /* private mode */ }
  }

  /** Appended to a request only when it would actually mean something. */
  param(): string {
    return this.active() ? `branchId=${this.branchId()}` : '';
  }
}

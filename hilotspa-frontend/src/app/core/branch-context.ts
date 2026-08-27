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
 * Held in memory on purpose. Persisting it would mean an administrator returns
 * tomorrow still silently scoped to one branch, and every figure they read
 * would be a subset they did not ask for.
 */
@Injectable({ providedIn: 'root' })
export class BranchContext {
  readonly branchId = signal<string | null>(null);
  readonly branch = signal<Branch | null>(null);

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
  }

  leave(): void {
    this.branchId.set(null);
    this.branch.set(null);
  }

  /** Appended to a request only when it would actually mean something. */
  param(): string {
    return this.active() ? `branchId=${this.branchId()}` : '';
  }
}

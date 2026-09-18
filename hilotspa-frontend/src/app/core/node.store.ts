import { Injectable, computed, inject, signal } from '@angular/core';
import { NodeIdentity, PublicApi } from './public.api';

/**
 * Which node is this browser talking to (task 3.5).
 *
 * Fetched once and shared, because two very different screens need the same
 * answer: the top bar, which has to name the branch instead of the hardcoded
 * "Bulan, Sorsogon" it carried while there was only ever one node; and the
 * assessment, which has to file the booking against THIS node's branch.
 *
 * The second is the one that matters. A customer has no token and therefore no
 * branch of their own, so the review screen used to fall back to the first
 * branch the API returned - alphabetically Bulan - which meant a client
 * booking on the Daraga node created a Bulan appointment. Every guarantee in
 * this architecture rests on each node writing only its own partition, and the
 * default was quietly breaking it at the point of entry.
 *
 * Not held in storage of any kind. The answer belongs to the SERVER that
 * answered, and a value cached from the last node the browser spoke to would
 * be wrong in exactly the case two nodes exist for.
 */
@Injectable({ providedIn: 'root' })
export class NodeStore {
  private api = inject(PublicApi);

  readonly node = signal<NodeIdentity | null>(null);

  /** The branch to show in the bar. Empty until the call lands, and empty
   *  forever on a node that declares none - never a guess. */
  readonly label = computed(() => this.node()?.branchName ?? '');

  private pending: Promise<NodeIdentity | null> | null = null;

  /**
   * Resolve once, then hand back the same answer.
   *
   * A FAILED call clears the memo rather than caching the failure. Caching it
   * would mean one flaky moment at page load leaves every later booking on
   * this tab falling back to the first branch - the exact bug this store
   * exists to remove, reintroduced by an error path.
   */
  ensure(): Promise<NodeIdentity | null> {
    if (!this.pending) {
      this.pending = this.api.node()
        .then(n => { this.node.set(n); return n; })
        .catch(() => { this.pending = null; return null; });
    }
    return this.pending;
  }
}

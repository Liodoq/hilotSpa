import { Injectable, computed, signal } from '@angular/core';

/**
 * Task 3.7 - is this device actually connected, and to what?
 *
 * The paper claims local staff keep "uninterrupted, real-time access to the
 * data needed for optimized service delivery, even during internet outages".
 * That claim only means something if the system can TELL the two apart, so this
 * service tracks two separate states rather than one vague "offline":
 *
 *   offline    - the browser has no network at all. Nothing will work.
 *   unreachable - the browser has a network, but this node did not answer.
 *
 * The distinction is the whole architecture in one banner. At the branch, staff
 * reach their own node over the LAN, so an INTERNET outage does not touch them -
 * only their node going down does. A single "you are offline" message would
 * flatten that into something the panel could not check, and would be wrong more
 * often than it was right.
 *
 * Nothing here simulates or predicts. `offline` comes from the browser's own
 * events; `unreachable` is set only after a request has genuinely failed with no
 * HTTP response (status 0), and cleared by the first request that succeeds.
 */
@Injectable({ providedIn: 'root' })
export class Connectivity {

  /** The browser's own verdict. Never optimistic - `onLine` false is reliable. */
  readonly offline = signal(typeof navigator !== 'undefined' && !navigator.onLine);

  /**
   * The last call to our own API got no response at all.
   *
   * Status 0 specifically: a 500 means the server is very much reachable and
   * having a bad time, which is a different message and a different fix.
   */
  readonly unreachable = signal(false);

  /** When the trouble started, so the banner can stop shouting about a blip. */
  readonly since = signal<number | null>(null);

  /** Either kind of trouble. What most screens actually want to ask. */
  readonly degraded = computed(() => this.offline() || this.unreachable());

  constructor() {
    if (typeof window === 'undefined') {
      return;
    }
    window.addEventListener('offline', () => {
      this.offline.set(true);
      this.since.update(t => t ?? Date.now());
    });
    window.addEventListener('online', () => {
      this.offline.set(false);
      // Do NOT clear `unreachable` here. The wifi coming back says nothing about
      // whether the branch node is up, and claiming otherwise would put staff
      // back on a screen that still cannot save anything.
      if (!this.unreachable()) {
        this.since.set(null);
      }
    });
  }

  /** A request came back with no HTTP response. */
  noteUnreachable(): void {
    this.unreachable.set(true);
    this.since.update(t => t ?? Date.now());
  }

  /** A request succeeded, so whatever was wrong is over. */
  noteReachable(): void {
    if (this.unreachable()) {
      this.unreachable.set(false);
    }
    if (!this.offline()) {
      this.since.set(null);
    }
  }

  /**
   * One sentence, honest about what the client can still do.
   *
   * Deliberately does not promise that anything queues locally: there is no
   * service worker and no local write queue, so a message saying "we will send
   * it when you are back" would be a lie the client discovers by turning up.
   * What IS true is that an existing booking is on the server and unaffected.
   */
  message(role: 'CUSTOMER' | 'STAFF'): string {
    if (this.offline()) {
      return role === 'STAFF'
        ? 'This device has no network. The branch node cannot be reached from here — '
          + 'keep a paper note of anything booked until it returns.'
        : 'You are offline. You cannot book right now, but any visit you have already '
          + 'booked still stands.';
    }
    return role === 'STAFF'
      ? 'The branch node is not answering. Your connection is fine, so this is the node '
        + 'itself — keep a paper note of anything booked until it returns.'
      : 'We cannot reach the spa right now. Any visit you have already booked still '
        + 'stands; please try again shortly or call the branch.';
  }
}

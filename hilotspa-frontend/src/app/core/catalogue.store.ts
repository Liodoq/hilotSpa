import { Injectable, computed, inject, signal } from '@angular/core';
import { CatalogueEntry, OpsApi } from './ops.api';
import { BookingStore } from './booking.store';
import { AuthService } from './auth.service';
import { PublicApi, PublicService } from './public.api';

/**
 * The service menu, as the SERVER judges it for this client.
 *
 * Two things are deliberately not done here. Nothing is filtered — an excluded
 * service arrives with its reason and is shown greyed, because hiding it would
 * make a clinical decision invisible (§D3). And `suitable` is never recomputed
 * client-side: the rule lives in ServiceProtocol, in the database, signed by the
 * bone setter. A copy of it in TypeScript would be a second source of truth that
 * nobody signed.
 */
@Injectable({ providedIn: 'root' })
export class CatalogueStore {
  private api = inject(OpsApi);
  private pub = inject(PublicApi);
  private auth = inject(AuthService);
  private bookings = inject(BookingStore);

  /**
   * True when the menu came from the PUBLIC endpoint — a visitor who has not
   * signed in.
   *
   * The screens need to know, because everything protocol-related is absent in
   * that case. Showing "no exclusions apply to your assessment" to someone
   * without an assessment would be a claim about a judgement that was never
   * made, which is the same class of untruth as B91.
   */
  readonly anonymous = signal(false);

  readonly entries = signal<CatalogueEntry[]>([]);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);

  /** The assessment the menu was judged against, or null if there is none. */
  readonly formId = signal<string | null>(null);

  readonly suitable = computed(() => this.entries().filter(e => e.suitable));
  readonly excluded = computed(() => this.entries().filter(e => !e.suitable));

  /** True when no assessment exists, so nothing has actually been judged yet. */
  readonly unjudged = computed(() => this.formId() === null);

  async load(force = false): Promise<void> {
    if (this.loading()) return;
    if (this.loaded() && !force) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      if (!this.auth.token()) {
        // A visitor browsing the menu before they have an account. They get the
        // treatments and nothing else: no protocol rule, no contraindication
        // verdict, no availability. Those are judgements about a named client,
        // and there is no client here.
        this.anonymous.set(true);
        this.formId.set(null);
        this.entries.set((await this.pub.services()).map(toEntry));
        this.loaded.set(true);
        return;
      }

      this.anonymous.set(false);
      if (!this.bookings.loaded()) await this.bookings.load();
      // The newest assessment is what the menu is judged against. history is
      // already sorted newest-first by the store.
      const latest = this.bookings.history()[0]?.id || null;
      this.formId.set(latest);
      this.entries.set(await this.api.catalogue(latest ?? undefined));
      this.loaded.set(true);
    } catch {
      this.entries.set([]);
      this.error.set('We could not load the service menu. Please try again.');
    } finally {
      this.loading.set(false);
    }
  }

  find(id: string): CatalogueEntry | undefined {
    return this.entries().find(e => e.serviceId === id);
  }

  clear(): void {
    this.entries.set([]);
    this.formId.set(null);
    this.loaded.set(false);
    this.anonymous.set(false);
    this.error.set(null);
  }
}

/**
 * A public treatment, shaped like a catalogue entry so both screens can render
 * one list.
 *
 * `suitable: true` and `rule: null` are the honest values: nothing was
 * excluded because nothing was judged. `rule: 'NEUTRAL'` would be a lie — it
 * would say the protocol table looked at this and had no opinion, when in fact
 * the protocol table was never consulted.
 */
function toEntry(s: PublicService): CatalogueEntry {
  return {
    serviceId: s.id,
    name: s.name,
    durationMinutes: s.durationMinutes,
    price: s.price,
    suitable: true,
    rule: null,
    reason: null,
    imageName: s.imageName,
  };
}

/**
 * Every service seeds at ₱0.00 until the spa hands over its rate card.
 * Printing "₱ 0" reads as free; this reads as what it is. Delete this helper
 * the day real prices land — it should not quietly become permanent.
 */
export function priceLabel(price: number | null | undefined): string {
  return price == null || Number(price) <= 0
    ? 'Ask at the counter'
    : `₱ ${Number(price).toLocaleString('en-PH')}`;
}

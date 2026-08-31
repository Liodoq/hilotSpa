import { Component, OnInit, inject, signal } from '@angular/core';
import { AppNav } from '../../shared/app-nav/app-nav';
import { PublicApi, PublicSpa } from '../../core/public.api';

/**
 * Contact details, and deliberately not a contact form.
 *
 * A form would need somewhere to send mail from, and this system has no mail
 * path. A form that silently files a message nobody reads is worse than no
 * form: the client believes they have made contact and waits. Every channel on
 * this page is one that actually reaches the spa today.
 *
 * The details come from configuration rather than hardcoded markup, so the
 * trade name can change in one line when the adviser settles §A3.
 */
@Component({
  selector: 'app-contact',
  imports: [AppNav],
  template: `
<app-nav />
<main class="wrap">
  <div class="col">
    <h1 class="t-l">Contact {{ spa()?.name || 'us' }}</h1>
    <p class="s" style="font-size:20px;margin-top:8px">
      Call, message, or simply walk in. Walk-ins are always welcome — the front desk can book
      you without an account.</p>

    @if (loading()) {
      <p class="help" style="margin-top:24px">Loading…</p>
    } @else {
      <div class="cards">
        @if (spa()?.phone) {
          <div class="card">
            <div class="k">Phone</div>
            <a class="v link" [href]="'tel:' + spa()?.phone">{{ spa()?.phone }}</a>
            <p class="help">Fastest way to reach the branch during opening hours.</p>
          </div>
        }
        @if (spa()?.facebook) {
          <div class="card">
            <div class="k">Facebook</div>
            <a class="v link" [href]="spa()?.facebook" target="_blank" rel="noopener">Message us</a>
            <p class="help">We reply here most days.</p>
          </div>
        }
        <div class="card">
          <div class="k">Where we are</div>
          <div class="v">{{ spa()?.address || 'Bulan, Sorsogon' }}</div>
          @if (spa()?.mapsUrl) {
            <p><a class="link" [href]="spa()?.mapsUrl" target="_blank" rel="noopener">Open in Maps</a></p>
          }
        </div>
        <div class="card">
          <div class="k">Opening hours</div>
          <div class="v">{{ spa()?.hours || 'Open daily' }}</div>
          <p class="help">Last booking one hour before closing.</p>
        </div>
      </div>

      @if (!spa()?.phone && !spa()?.facebook) {
        <div class="note" style="margin-top:22px"><span class="b"></span>
          <span>Our phone number and Facebook page are not published here yet. Please visit the
          branch — we are open during the hours above.</span></div>
      }
    }
  </div>
</main>
  `,
  styles: [`
    :host { display: block; min-height: 100dvh; background: var(--color-cream); }
    .wrap { padding: 40px 24px 80px; display: flex; justify-content: center; }
    .col { width: 100%; max-width: 900px; }
    .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 18px; margin-top: 30px; }
    .card { padding: 22px; display: flex; flex-direction: column; gap: 6px; }
    .k { font-size: 14px; letter-spacing: 1.8px; text-transform: uppercase;
      color: var(--color-ink-600); }
    .v { font-size: 21px; color: var(--color-forest-900); font-weight: 600; }
    .card p { margin: 6px 0 0; }
  `],
})
export class Contact implements OnInit {
  private api = inject(PublicApi);
  protected spa = signal<PublicSpa | null>(null);
  protected loading = signal(true);

  async ngOnInit(): Promise<void> {
    try { this.spa.set(await this.api.spa()); } catch { /* the card defaults cover it */ }
    finally { this.loading.set(false); }
  }
}

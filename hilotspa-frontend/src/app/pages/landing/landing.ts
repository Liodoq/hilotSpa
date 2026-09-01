import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { BodyMap } from '../../shared/body-map/body-map';
import { AuthService } from '../../core/auth.service';
import { PublicApi, PublicSpa } from '../../core/public.api';
import { priceLabel } from '../../core/catalogue.store';
import { PainPoint } from '../../core/assessment.store';
import { Hotspot, sideLabel } from '../../core/body-hotspots';
import { BodyView, REGIONS } from '../../core/models';

/**
 * The front door.
 *
 * Everything here comes from GET /api/v1/public/spa — the only unauthenticated
 * read in the system: the spa's own details and its live menu. No protocol
 * rules, no availability, no counts. The menu is the SAME table the assistant
 * works from, so this page cannot drift from what the spa actually sells the
 * way a hand-written marketing page would.
 *
 * The hero is the body map rather than a photograph, because the map IS the
 * thing that makes this spa different from every other spa in Sorsogon. A
 * stranger arrives, taps where it hurts, and has already used the product. It
 * promises nothing clinical: tapping names the area and says what happens with
 * it, which is exactly what the real assessment does.
 */
@Component({
  selector: 'app-landing',
  imports: [AppNav, RouterLink, BodyMap],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing implements OnInit {
  private api = inject(PublicApi);
  private router = inject(Router);
  protected auth = inject(AuthService);

  protected priceLabel = priceLabel;

  protected spa = signal<PublicSpa | null>(null);
  protected loading = signal(true);
  protected failed = signal(false);

  /** Six on the front page. The rest are one tap away on /services. */
  protected featured = computed(() => (this.spa()?.services ?? []).slice(0, 6));
  protected team = computed(() => this.spa()?.therapists ?? []);
  protected more = computed(() => Math.max(0, (this.spa()?.services ?? []).length - 6));

  // ------------------------------------------------------------- the hero map
  protected view = signal<BodyView>('BACK');
  protected picked = signal<Hotspot | null>(null);

  /**
   * The tapped spot, in the shape BodyMap draws.
   *
   * Severity 5 because nothing here asks how bad it is — the marker only has to
   * be visible. The real question comes in the assessment, where the answer is
   * kept.
   */
  protected marks = computed<PainPoint[]>(() => {
    const h = this.picked();
    if (!h) { return []; }
    return [{
      key: 'demo', hotspotId: h.id, view: h.view, x: h.x, y: h.y,
      region: h.region, side: h.side, score: 5, qualities: [],
    }];
  });

  /** "Lower back · left", the way the form itself names a place. */
  protected pickedLabel = computed(() => {
    const h = this.picked();
    if (!h) { return ''; }
    const region = REGIONS.find(r => r.value === h.region)?.label ?? h.region;
    const side = sideLabel(h.side);
    return side ? `${region} · ${side}` : region;
  });

  place(h: Hotspot): void { this.picked.set(h); }
  showView(v: BodyView): void { this.view.set(v); this.picked.set(null); }

  async ngOnInit(): Promise<void> {
    try {
      this.spa.set(await this.api.spa());
    } catch {
      // The spa is real whether or not the server answers. Say what we know and
      // let people reach the branch rather than showing an error page.
      this.failed.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  /** One button, two destinations: straight in for a client who is already
   *  signed in, the sign-up for everyone else. */
  start(): void {
    this.router.navigateByUrl(this.auth.token() ? '/assessment/intent' : '/register');
  }

  initial(name: string): string { return (name || '?').charAt(0).toUpperCase(); }

  photo(file: string | null): string | null {
    return file ? `url(/services/${file})` : null;
  }
}

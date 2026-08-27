import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { Toast } from '../../shared/toast/toast';
import { ToastService } from '../../core/toast.service';
import { CatalogueStore, priceLabel } from '../../core/catalogue.store';
import { CatalogueEntry } from '../../core/ops.api';

/**
 * C8 — the service menu, and the screen where §D3 becomes visible.
 *
 * Every row comes from GET /assistant/catalogue, which reads the Massage table
 * and judges it against this client's own assessment using ServiceProtocol.
 * Excluded services are shown greyed WITH the reason rather than silently
 * removed: the client can see that a decision was made and what rule made it,
 * which is the difference between enforcing the spa's protocol and quietly
 * giving medical advice.
 */
@Component({
  selector: 'app-services',
  imports: [AppNav, Toast, RouterLink],
  templateUrl: './services.html',
  styleUrl: './services.scss',
})
export class Services implements OnInit {
  private router = inject(Router);
  protected toast = inject(ToastService);
  protected cat = inject(CatalogueStore);

  protected priceLabel = priceLabel;

  protected filters = ['Recommended for you', 'All services', '45 minutes or less'];
  filter = signal(this.filters[0]);

  hidden = computed(() => this.cat.excluded());
  hiddenNames = computed(() => this.hidden().map(s => s.name).join(' and '));

  shown = computed<CatalogueEntry[]>(() => {
    const all = this.cat.entries();
    const f = this.filter();
    if (f === 'All services') return all;
    if (f === '45 minutes or less') return all.filter(s => (s.durationMinutes ?? 0) <= 45);
    // "Recommended for you" — INDICATED first, then neutral, exclusions last but
    // still present. Ordering is a hint; hiding would be a decision.
    return all.slice().sort((a, b) => rank(a) - rank(b));
  });

  ngOnInit(): void { void this.cat.load(); }

  /** Best match = the rule engine marked it INDICATED for this assessment. */
  best(s: CatalogueEntry): boolean { return s.rule === 'INDICATED'; }

  open(s: CatalogueEntry): void {
    if (!s.suitable) {
      this.toast.show(`${s.name} is not advised — ${s.reason || 'see your assessment'}`, 3200);
      return;
    }
    this.router.navigateByUrl(`/services/${s.serviceId}`);
  }

  /** Quotes the actual stored rule, not a sentence written in the template. */
  explain(): void {
    const s = this.hidden()[0];
    this.toast.show(s
      ? `Rule: ${s.name} — ${s.rule}. ${s.reason || ''}`.trim()
      : 'No exclusions apply to your assessment.', 3600);
  }

  retry(): void { void this.cat.load(true); }
}

function rank(s: CatalogueEntry): number {
  if (!s.suitable) return 2;
  return s.rule === 'INDICATED' ? 0 : 1;
}

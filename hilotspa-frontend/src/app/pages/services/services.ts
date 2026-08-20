import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { Toast } from '../../shared/toast/toast';
import { ToastService } from '../../core/toast.service';
import { DemoService, SERVICES } from '../../core/demo';

/**
 * C8 — the service menu, and the screen where §D3 becomes visible.
 *
 * Excluded services are shown greyed out with the reason, rather than silently
 * removed. The client can see that a decision was made, who made it, and why —
 * which is the difference between enforcing the spa's protocol and quietly
 * giving medical advice.
 */
@Component({
  selector: 'app-services',
  imports: [AppNav, Toast, RouterLink],
  templateUrl: './services.html',
  styleUrl: './services.scss',
})
export class Services {
  private router = inject(Router);
  protected toast = inject(ToastService);

  protected filters = ['Recommended for you', 'All services', 'Under ₱600', '30 minutes'];
  filter = signal(this.filters[0]);

  private all = SERVICES;
  hidden = computed(() => this.all.filter(s => !s.suitable));
  hiddenNames = computed(() => this.hidden().map(s => s.name).join(' and '));

  shown = computed<DemoService[]>(() => {
    const f = this.filter();
    if (f === 'All services') return this.all;
    if (f === 'Under ₱600') return this.all.filter(s => s.suitable && s.price < 600);
    if (f === '30 minutes') return this.all.filter(s => s.suitable && s.minutes <= 45);
    return this.all;   // "Recommended for you" — suitable first, exclusions still visible
  });

  choose(s: DemoService): void {
    this.toast.show(`${s.name} selected — let's find a time`);
    setTimeout(() => this.router.navigateByUrl('/book'), 500);
  }

  explain(): void {
    this.toast.show('Rule: Ventosa × high blood pressure — CONTRAINDICATED, signed 12 Aug 2026', 3400);
  }
}

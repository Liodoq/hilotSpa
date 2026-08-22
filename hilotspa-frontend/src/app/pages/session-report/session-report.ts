import { Component, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { BodyMap } from '../../shared/body-map/body-map';
import { Toast } from '../../shared/toast/toast';
import { BookingStore } from '../../core/booking.store';
import { PainPoint } from '../../core/assessment.store';
import { severityClass } from '../../core/region.util';

/**
 * A finished session, read only.
 *
 * "View report" on the home page used to open /assessment/review — the live,
 * editable wizard step. A completed session could be rewritten from the home
 * page, and the assessment being edited was the NEW draft, not the old record.
 * A log of what happened is not a form.
 */
@Component({
  selector: 'app-session-report',
  imports: [AppNav, BodyMap, Toast],
  templateUrl: './session-report.html',
  styleUrl: './session-report.scss',
})
export class SessionReport {
  private router = inject(Router);
  private store = inject(BookingStore);

  /** bound from the route via withComponentInputBinding() */
  id = input<string>('');

  session = computed(() => this.store.find(this.id()));
  /** This session's OWN marks. It used to render LAST_POINTS, so every report
   *  showed the same demo body map regardless of which session was opened. */
  protected points = computed<PainPoint[]>(() => this.session()?.points ?? []);
  protected sev = severityClass;

  back(): void { this.router.navigateByUrl('/home'); }
  again(): void { this.router.navigateByUrl('/book'); }
}

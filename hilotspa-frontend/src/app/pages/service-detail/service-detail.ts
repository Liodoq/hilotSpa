import { Component, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { Toast } from '../../shared/toast/toast';
import { ToastService } from '../../core/toast.service';
import { AssessmentStore } from '../../core/assessment.store';
import { ProfileStore } from '../../core/profile.store';
import { DemoService, SERVICES } from '../../core/demo';

/**
 * A service on its own page, with a photo.
 *
 * "Book this" does NOT create a booking. It starts the pre-assessment, because
 * Process Rule #2 gates the assistant behind a completed one — and because the
 * contraindication filter cannot run against an assessment that does not exist.
 * The chosen service is remembered so the assistant opens with it.
 */
@Component({
  selector: 'app-service-detail',
  imports: [AppNav, Toast],
  templateUrl: './service-detail.html',
  styleUrl: './service-detail.scss',
})
export class ServiceDetail {
  private router = inject(Router);
  private store = inject(AssessmentStore);
  private profile = inject(ProfileStore);
  private toast = inject(ToastService);

  id = input<string>('');
  service = computed<DemoService | undefined>(() => SERVICES.find(s => s.id === this.id()));

  back(): void { this.router.navigateByUrl('/services'); }

  book(s: DemoService): void {
    this.store.resetAll();
    this.store.wantedService.set(s.name);
    this.toast.show(`${s.name} chosen — a short pre-assessment first`, 2800);
    this.router.navigateByUrl(this.profile.isComplete()
      ? '/assessment/intent'
      : '/profile?next=/assessment/intent');
  }
}

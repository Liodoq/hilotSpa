import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { Toast } from '../../shared/toast/toast';
import { DatePicker } from '../../shared/date-picker/date-picker';
import { ProfileStore } from '../../core/profile.store';
import { ToastService } from '../../core/toast.service';

/**
 * The demographics block from the paper intake form — captured ONCE, on the
 * profile, instead of as step 2 of every assessment.
 *
 * Reached either from the home page, or by profileGuard when someone starts an
 * assessment without one. In that second case it carries a ?next= so they land
 * back where they were going.
 */
@Component({
  selector: 'app-profile',
  imports: [AppNav, Toast, DatePicker],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  protected store = inject(ProfileStore);
  protected toast = inject(ToastService);

  protected sexes = ['Female', 'Male'];
  protected statuses = ['Single', 'Married', 'Widowed', 'Separated'];

  protected p = this.store.profile;

  /** Fields are read-only until Edit is pressed. An incomplete profile — or one
   *  reached mid-assessment by profileGuard — opens straight into edit mode,
   *  because there is nothing to protect yet. */
  editing = signal(!this.store.isComplete());
  private snapshot = { ...this.store.profile() };

  startEdit(): void {
    this.snapshot = { ...this.store.profile() };
    this.editing.set(true);
  }

  /** True when the guard sent them here mid-flow. */
  returning = computed(() => !!this.route.snapshot.queryParamMap.get('next'));

  val(ev: Event): string { return (ev.target as HTMLInputElement | HTMLSelectElement).value; }

  num(ev: Event): number | null {
    const raw = (ev.target as HTMLInputElement).value.trim();
    if (raw === '') return null;
    const n = Number(raw);
    return Number.isFinite(n) ? n : null;
  }

  save(): void {
    this.store.save();
    // TODO — POST to /api/v1/demographics once a CUSTOMER is allowed to (B43).
    const next = this.route.snapshot.queryParamMap.get('next');
    this.editing.set(false);
    this.wasComplete = true;
    this.toast.show('Saved to your profile');
    if (next) setTimeout(() => this.router.navigateByUrl(next), 450);
  }

  cancel(): void {
    if (this.returning() || !this.wasComplete) { this.router.navigateByUrl('/home'); return; }
    this.store.profile.set({ ...this.snapshot });   // discard, do not persist
    this.editing.set(false);
    this.toast.show('No changes made');
  }

  private wasComplete = this.store.isComplete();
}

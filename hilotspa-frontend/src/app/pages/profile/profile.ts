import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { Toast } from '../../shared/toast/toast';
import { DatePicker } from '../../shared/date-picker/date-picker';
import { ProfileStore } from '../../core/profile.store';
import { ToastService } from '../../core/toast.service';

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July',
  'August', 'September', 'October', 'November', 'December'];

/**
 * The demographics block from the paper intake form — captured ONCE, on the
 * profile, instead of as step 2 of every assessment.
 *
 * Reached either from the home page, or by profileGuard when someone starts an
 * assessment without one. In that second case it carries a ?next= so they land
 * back where they were going.
 *
 * The redesign changed how stored values are DRAWN, not what they mean. Every
 * behaviour below is untouched: edit is still locked behind a button, cancel
 * still restores the snapshot without persisting, save still refuses to close
 * the editor or mark the profile usable when the server rejected it (B43), and
 * ?next= still carries the client back into the wizard.
 */
@Component({
  selector: 'app-profile',
  imports: [AppNav, Toast, DatePicker, RouterLink],
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

  /** Same wording the date picker uses, so the value does not change shape
   *  when you press Edit. */
  birthLabel = computed(() => {
    const v = this.p().birthDate;
    if (!v) return '';
    const d = new Date(v);
    if (Number.isNaN(d.getTime())) return '';
    return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
  });

  /** Read-mode line for two optional numbers that are usually both blank. */
  sizeLabel = computed(() => {
    const { height, weight } = this.p();
    if (!height && !weight) return '';
    return `${height ? height + ' cm' : '—'} · ${weight ? weight + ' kg' : '—'}`;
  });

  val(ev: Event): string { return (ev.target as HTMLInputElement | HTMLSelectElement).value; }

  num(ev: Event): number | null {
    const raw = (ev.target as HTMLInputElement).value.trim();
    if (raw === '') return null;
    const n = Number(raw);
    return Number.isFinite(n) ? n : null;
  }

  saving = signal(false);

  async save(): Promise<void> {
    if (this.saving()) return;
    this.saving.set(true);
    try {
      await this.store.save();
    } catch {
      // Do NOT close the editor and do NOT mark it complete. Letting the client
      // walk into the wizard on a profile the server never stored is the whole
      // reason B43 mattered.
      this.toast.show('Could not save your profile. Please try again.');
      return;
    } finally {
      this.saving.set(false);
    }
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

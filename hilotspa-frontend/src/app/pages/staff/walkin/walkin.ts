import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { BranchContext } from '../../../core/branch-context';
import { ToastService } from '../../../core/toast.service';
import { CatalogueEntry, OpsApi, RoomDto, TherapistDto } from '../../../core/ops.api';
import { priceLabel } from '../../../core/catalogue.store';
import { describeHttpError } from '../../../core/http-error';

/**
 * S5 — the staff-entered appointment. BookingSource.STAFF_MANUAL.
 *
 * A walk-in has no account, which is why B77 had to be settled in the schema
 * before this screen could do anything: `Appointment.customer` is nullable now
 * and a `walkInName` identifies the visit instead. No User row is invented at
 * the counter — an account that cannot log in would be a false entry in the
 * accounts screen.
 *
 * No pre-assessment is required here. Process Rule #2 gates the ASSISTANT
 * behind a completed assessment, not the front desk; refusing to record a
 * paying client who has not filled in a questionnaire would be the app
 * obstructing the business it is meant to serve.
 *
 * The therapist and room are assigned by the server, in the same transaction
 * the assistant uses — so the counter and the chatbot cannot award the same
 * therapist.
 */
@Component({
  selector: 'app-staff-walkin',
  imports: [DashShell, RouterLink],
  templateUrl: './walkin.html',
  styleUrl: './walkin.scss',
})
export class StaffWalkin implements OnInit {
  private api = inject(OpsApi);
  /** Null for staff — the server takes their branch from the token. Set only
   *  when an ADMINISTRATOR has switched into a branch (Figure 3.3). */
  protected ctx = inject(BranchContext);
  private router = inject(Router);
  protected toast = inject(ToastService);
  protected priceLabel = priceLabel;

  services = signal<CatalogueEntry[]>([]);
  staff = signal<TherapistDto[]>([]);
  rooms = signal<RoomDto[]>([]);
  loading = signal(true);
  saving = signal(false);
  error = signal<string | null>(null);

  name = signal('');
  contact = signal('');
  serviceId = signal('');
  date = signal(todayIso());
  time = signal(nextHalfHour());
  notes = signal('');

  freeStaff = computed(() => this.staff().filter(s => s.status === 'AVAILABLE'));
  freeRooms = computed(() => this.rooms().filter(r => r.active));
  chosen = computed(() => this.services().find(s => s.serviceId === this.serviceId()));

  /** The server refuses these too — this only saves the client a round trip. */
  canSave = computed(() =>
    !!this.name().trim() && !!this.serviceId() && !!this.date() && !!this.time());

  /** Nobody free means the server will answer 409, so say it before they type. */
  blocked = computed(() => !this.freeStaff().length || !this.freeRooms().length);

  async ngOnInit(): Promise<void> { await this.load(); }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const b = this.ctx.branchId();
      const [cat, staff, rooms] = await Promise.all([
        this.api.catalogue(), this.api.therapists(b), this.api.rooms(b),
      ]);
      this.services.set(cat);
      this.staff.set(staff);
      this.rooms.set(rooms);
      if (!this.serviceId() && cat.length) this.serviceId.set(cat[0].serviceId);
    } catch {
      this.error.set('We could not load the menu, therapists and rooms.');
    } finally {
      this.loading.set(false);
    }
  }

  val(ev: Event): string {
    return (ev.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value;
  }

  async record(): Promise<void> {
    if (!this.canSave() || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    try {
      // Sent without a timezone offset on purpose. The server works in the
      // spa's timezone; a browser offset would silently shift the booking.
      await this.api.bookWalkIn({
        serviceId: this.serviceId(),
        start: `${this.date()}T${this.time()}:00`,
        name: this.name().trim(),
        contact: this.contact().trim() || null,
        notes: this.notes().trim() || null,
        idempotencyKey: `walkin-${this.name().trim()}-${this.date()}T${this.time()}`,
      });
      this.toast.show(
        `Recorded — ${this.name().trim()}, ${this.chosen()?.name}. Source STAFF_MANUAL.`, 3400);
      setTimeout(() => this.router.navigateByUrl('/staff/dashboard'), 700);
    } catch (e: unknown) {
      this.error.set(describeHttpError(e,
        'We could not record that. Nothing was saved.'));
    } finally {
      this.saving.set(false);
    }
  }
}

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

/** Defaults to the next half hour, because that is what the counter books. */
function nextHalfHour(): string {
  const d = new Date();
  d.setMinutes(d.getMinutes() > 30 ? 60 : 30, 0, 0);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

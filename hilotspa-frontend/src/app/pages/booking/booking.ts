import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { Toast } from '../../shared/toast/toast';
import { ToastService } from '../../core/toast.service';
import { FormsApi } from '../../core/forms.api';
import { BookingModel } from '../../core/models';

/** Statuses that still hold a therapist and a room, i.e. a live booking. */
const OPEN = new Set(['PENDING', 'CONFIRMED', 'IN_PROGRESS']);

/**
 * C10 — My bookings.
 *
 * It used to render NEXT_VISIT from demo.ts, so a brand-new account opened onto
 * a booking it had never made. Same family as B69/B70: placeholder data that
 * reads as somebody's real record.
 *
 * priceAtBooking is a snapshot and payment is at the counter, which is how
 * revenue is tracked without processing money.
 */
@Component({
  selector: 'app-booking',
  imports: [AppNav, Toast, RouterLink, DatePipe],
  templateUrl: './booking.html',
  styleUrl: './booking.scss',
})
export class Booking {
  protected toast = inject(ToastService);
  private api = inject(FormsApi);

  protected loading = signal(true);
  protected all = signal<BookingModel[]>([]);

  /**
   * EVERY upcoming visit, soonest first.
   *
   * This used to be `[0]` — the soonest one only. A client with two bookings saw
   * one of them and had no way to know the other existed, which is the same
   * class of mistake as B69: the screen quietly disagreeing with the database.
   * The assistant was answering "you have two" while this page showed one.
   */
  protected upcoming = computed(() => {
    const now = Date.now();
    return this.all()
      .filter(x => new Date(x.start).getTime() >= now && OPEN.has(x.status))
      .sort((p, q) => new Date(p.start).getTime() - new Date(q.start).getTime());
  });

  /** The next one, highlighted — but never at the expense of hiding the rest. */
  protected b = computed(() => this.upcoming()[0]);
  protected later = computed(() => this.upcoming().slice(1));

  /**
   * Everything that is no longer ahead of you — B101.
   *
   * This was headed "Past visits" and filtered on
   * `start < now || status === 'CANCELLED'`, so a visit CANCELLED for next
   * Wednesday appeared under a heading promising the past, beside a no-show and
   * beside a visit from three days ago that nobody had closed off. Three
   * different kinds of thing under one wrong label, each showing its raw
   * database enum.
   *
   * The set is right - a client does want to see a booking they cancelled - so
   * what changed is the NAME and the labels. It is a history, not a list of
   * visits that happened.
   */
  protected history = computed(() => {
    const now = Date.now();
    return this.all()
      .filter(x => new Date(x.start).getTime() < now || x.status === 'CANCELLED')
      .sort((p, q) => new Date(q.start).getTime() - new Date(p.start).getTime());
  });

  /**
   * Visits whose time has passed and which the spa has never closed off.
   *
   * The client is not at fault and must not be told they were: it says "not
   * recorded", never "no show". Only a person who was in the room can say which
   * it was, and until they do this visit produces no after-score at all - which
   * is exactly what the staff calendar exists to surface.
   */
  protected unrecorded = computed(() => {
    const now = Date.now();
    return this.all().filter(x =>
      new Date(x.start).getTime() < now && OPEN.has(x.status));
  });

  /** What a client should read. Never the enum. */
  statusLabel(v: BookingModel): string {
    switch (v.status) {
      case 'COMPLETED': return 'Completed';
      case 'NO_SHOW':   return 'Did not attend';
      case 'CANCELLED': return 'Cancelled';
      default:
        return new Date(v.start).getTime() < Date.now() ? 'Not recorded' : 'Booked';
    }
  }

  statusTone(v: BookingModel): string {
    switch (v.status) {
      case 'COMPLETED': return 'ok';
      case 'CANCELLED': return 'bad';
      case 'NO_SHOW':   return 'warn';
      default:          return 'mute';
    }
  }

  /** The booking awaiting a second tap, and the one currently being cancelled.
   *  Keyed by id so a page showing three visits cannot confirm the wrong one. */
  protected confirming = signal<string | null>(null);
  protected cancelling = signal<string | null>(null);

  askCancel(id: string): void { this.confirming.set(id); }
  keepIt(): void { this.confirming.set(null); }

  /**
   * Cancel — 2.32.
   *
   * The list is reloaded from the server rather than patched here. If the server
   * refused, the screen has to show what the database says; a page that quietly
   * disagrees with it is the B69 family of bug all over again.
   */
  async cancel(v: BookingModel): Promise<void> {
    if (this.cancelling()) return;
    this.cancelling.set(v.id);
    try {
      await this.api.cancelBooking(v.id, 'Cancelled by the client from My bookings');
      this.confirming.set(null);
      this.toast.show(`Cancelled — ${v.serviceName}, ${v.label}. Nothing to pay.`, 4200);
      await this.load();
    } catch (e: unknown) {
      const status = (e as { status?: number })?.status;
      this.toast.show(status === 409
        ? 'That visit has already started. Please speak to the front desk.'
        : 'We could not cancel that just now. Please try again, or call the branch.', 4200);
    } finally {
      this.cancelling.set(null);
    }
  }

  constructor() { void this.load(); }

  private async load(): Promise<void> {
    try {
      this.all.set(await this.api.myBookings());
    } catch {
      this.all.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * A real calendar file, not a toast that claimed one.
   *
   * .ics is a plain-text standard every calendar app reads, so this works
   * offline and needs no Google account - which matters at a spa in Bulan.
   */
  addToCalendar(v: BookingModel): void {
    const stamp = (iso: string) =>
      new Date(iso).toISOString().replace(/[-:]/g, '').split('.')[0] + 'Z';
    const ics = [
      'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//HilotSpa//EN',
      'BEGIN:VEVENT',
      `UID:${v.id}@hilotspa`,
      `DTSTAMP:${stamp(new Date().toISOString())}`,
      `DTSTART:${stamp(v.start)}`,
      `DTEND:${stamp(v.end)}`,
      `SUMMARY:${v.serviceName} — Knead Wellness Spa`,
      `LOCATION:${v.branch}`,
      `DESCRIPTION:${v.durationMinutes} minutes with ${v.therapist}, ${v.room}. `
        + `PHP ${v.price}, pay at the counter.`,
      'END:VEVENT', 'END:VCALENDAR',
    ].join('\r\n');

    const url = URL.createObjectURL(new Blob([ics], { type: 'text/calendar' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `hilotspa-${v.start.slice(0, 10)}.ics`;
    a.click();
    URL.revokeObjectURL(url);
    this.toast.show('Calendar file downloaded');
  }
}

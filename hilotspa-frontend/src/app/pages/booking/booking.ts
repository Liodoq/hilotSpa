import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { Toast } from '../../shared/toast/toast';
import { ToastService } from '../../core/toast.service';
import { FormsApi } from '../../core/forms.api';
import { BookingModel } from '../../core/models';

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
      .filter(x => new Date(x.start).getTime() >= now && x.status !== 'CANCELLED')
      .sort((p, q) => new Date(p.start).getTime() - new Date(q.start).getTime());
  });

  /** The next one, highlighted — but never at the expense of hiding the rest. */
  protected b = computed(() => this.upcoming()[0]);
  protected later = computed(() => this.upcoming().slice(1));

  protected past = computed(() => {
    const now = Date.now();
    return this.all()
      .filter(x => new Date(x.start).getTime() < now || x.status === 'CANCELLED')
      .sort((p, q) => new Date(q.start).getTime() - new Date(p.start).getTime());
  });

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

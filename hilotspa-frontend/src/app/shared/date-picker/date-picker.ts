import { Component, computed, input, output, signal } from '@angular/core';

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June',
                'July', 'August', 'September', 'October', 'November', 'December'];

/**
 * A rounded date picker in the spa's own palette.
 *
 * The native <input type="date"> popup is small, blue, and styled by the
 * browser — it cannot be brought into the design, and its day targets are far
 * below the 44px NFR#4 floor. This one uses 44px circular targets and puts
 * month and year in selects, because the common case here is a birth date and
 * nobody should page back fifty years one month at a time.
 */
@Component({
  selector: 'app-date-picker',
  templateUrl: './date-picker.html',
  styleUrl: './date-picker.scss',
})
export class DatePicker {
  /** ISO yyyy-mm-dd, or null */
  value = input<string | null>(null);
  placeholder = input('Choose a date');
  disabled = input(false);
  /** how far back the year list goes */
  minYear = input(1920);

  changed = output<string | null>();

  open = signal(false);
  month = signal(new Date().getMonth());
  year = signal(new Date().getFullYear());

  protected months = MONTHS;
  protected dow = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

  years = computed(() => {
    const now = new Date().getFullYear();
    const out: number[] = [];
    for (let y = now; y >= this.minYear(); y--) out.push(y);
    return out;
  });

  display = computed(() => {
    const v = this.value();
    if (!v) return this.placeholder();
    const d = new Date(v);
    if (Number.isNaN(d.getTime())) return this.placeholder();
    return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
  });

  /** leading nulls pad the first row so the 1st lands on the right weekday */
  cells = computed<(number | null)[]>(() => {
    const first = new Date(this.year(), this.month(), 1).getDay();
    const days = new Date(this.year(), this.month() + 1, 0).getDate();
    const out: (number | null)[] = Array(first).fill(null);
    for (let d = 1; d <= days; d++) out.push(d);
    return out;
  });

  num(ev: Event): string { return (ev.target as HTMLSelectElement).value; }

  toggle(): void {
    if (this.disabled()) return;
    if (!this.open()) {
      const v = this.value();
      const d = v ? new Date(v) : new Date();
      if (!Number.isNaN(d.getTime())) { this.month.set(d.getMonth()); this.year.set(d.getFullYear()); }
    }
    this.open.set(!this.open());
  }

  shift(by: number): void {
    let m = this.month() + by;
    let y = this.year();
    if (m < 0) { m = 11; y--; }
    if (m > 11) { m = 0; y++; }
    this.month.set(m);
    this.year.set(y);
  }

  private iso(day: number): string {
    const m = String(this.month() + 1).padStart(2, '0');
    const d = String(day).padStart(2, '0');
    return `${this.year()}-${m}-${d}`;
  }

  isSelected(day: number): boolean { return this.value() === this.iso(day); }

  isToday(day: number): boolean {
    const t = new Date();
    return t.getFullYear() === this.year() && t.getMonth() === this.month() && t.getDate() === day;
  }

  pick(day: number): void { this.changed.emit(this.iso(day)); this.open.set(false); }
  clear(): void { this.changed.emit(null); }
}

import { Component, computed, input, output, signal } from '@angular/core';
import { BookingModel } from '../../core/models';

/**
 * A client's own visits, on a month grid.
 *
 * WHY THIS EXISTS. "Your visits" is two sorted lists - what is ahead of you and
 * what is behind. That answers "when is my next one", and answers badly the
 * question a client with a recurring treatment actually asks: "how often am I
 * coming, and is there anything the week after next?" A list makes the reader
 * do the date arithmetic. A month does it for them.
 *
 * IT OWNS NO DATA. Every visit shown here is already loaded by the page for its
 * lists. The calendar is a second VIEW of the same array, never a second
 * fetch - two requests for one truth is how a screen starts disagreeing with
 * itself (B69, B101).
 *
 * DATES ARE LOCAL, ALWAYS. Grouping by `toISOString().slice(0,10)` would file
 * every visit before 08:00 Manila under the previous day, because that method
 * converts to UTC first. A calendar that puts a visit on the wrong square is
 * worse than no calendar.
 */

/** A yyyy-mm-dd in the BROWSER'S zone. Never toISOString() - see above. */
function isoLocal(d: Date): string {
  const m = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

/** Monday. getDay() calls Sunday 0, which would split a weekend across rows. */
function mondayOf(d: Date): Date {
  const out = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const shift = (out.getDay() + 6) % 7;
  out.setDate(out.getDate() - shift);
  return out;
}

export interface CalCell {
  date: Date;
  iso: string;
  dayNum: number;
  inMonth: boolean;
  isToday: boolean;
  visits: BookingModel[];
}

@Component({
  selector: 'app-visit-calendar',
  standalone: true,
  templateUrl: './visit-calendar.html',
  styleUrl: './visit-calendar.scss',
})
export class VisitCalendar {
  /** Every visit the client has. Past, future and cancelled alike. */
  visits = input.required<BookingModel[]>();

  /** Raised when a day carrying visits is chosen. The page decides what to do. */
  pickDay = output<BookingModel[]>();

  /**
   * The month on show, held as its first day.
   *
   * Opens on the month of the NEXT visit rather than on today. A client looking
   * at this in the last week of a month almost always wants the one that
   * contains their next appointment, and landing on an empty grid reads as
   * "nothing booked" rather than "wrong month".
   */
  protected cursor = signal<Date>(new Date());
  private opened = false;

  protected selected = signal<string | null>(null);

  /** Visits filed by local calendar day. */
  private byDay = computed(() => {
    const map = new Map<string, BookingModel[]>();
    for (const v of this.visits()) {
      const key = isoLocal(new Date(v.start));
      const bucket = map.get(key);
      if (bucket) { bucket.push(v); } else { map.set(key, [v]); }
    }
    for (const list of map.values()) {
      list.sort((p, q) => new Date(p.start).getTime() - new Date(q.start).getTime());
    }
    return map;
  });

  protected monthLabel = computed(() =>
    this.cursor().toLocaleDateString(undefined, { month: 'long', year: 'numeric' }));

  protected weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

  /**
   * Six rows, always.
   *
   * A grid that is five rows one month and six the next makes the page jump
   * under the reader's thumb when they step between months. Fixed height is
   * worth the occasional row of greyed-out days.
   */
  protected weeks = computed<CalCell[][]>(() => {
    const c = this.cursor();
    const first = new Date(c.getFullYear(), c.getMonth(), 1);
    const start = mondayOf(first);
    const todayIso = isoLocal(new Date());
    const map = this.byDay();

    const out: CalCell[][] = [];
    const walker = new Date(start);
    for (let w = 0; w < 6; w++) {
      const row: CalCell[] = [];
      for (let d = 0; d < 7; d++) {
        const date = new Date(walker.getFullYear(), walker.getMonth(), walker.getDate());
        const iso = isoLocal(date);
        row.push({
          date,
          iso,
          dayNum: date.getDate(),
          inMonth: date.getMonth() === c.getMonth(),
          isToday: iso === todayIso,
          visits: map.get(iso) ?? [],
        });
        walker.setDate(walker.getDate() + 1);
      }
      out.push(row);
    }
    return out;
  });

  /** The chosen day's visits, for the panel under the grid. */
  protected chosen = computed<BookingModel[]>(() => {
    const key = this.selected();
    return key ? (this.byDay().get(key) ?? []) : [];
  });

  constructor() {
    // Jump to the next visit's month once, the first time visits arrive. Doing
    // it on every change would yank the grid back while the client is browsing.
    queueMicrotask(() => this.openOnNextVisit());
  }

  private openOnNextVisit(): void {
    if (this.opened) { return; }
    const list = this.visits();
    if (!list.length) { return; }
    this.opened = true;
    const now = Date.now();
    const next = list
      .filter(v => new Date(v.start).getTime() >= now && v.status !== 'CANCELLED')
      .sort((p, q) => new Date(p.start).getTime() - new Date(q.start).getTime())[0];
    if (next) {
      const d = new Date(next.start);
      this.cursor.set(new Date(d.getFullYear(), d.getMonth(), 1));
    }
  }

  protected step(months: number): void {
    const c = this.cursor();
    this.cursor.set(new Date(c.getFullYear(), c.getMonth() + months, 1));
    this.selected.set(null);
  }

  protected goToday(): void {
    const n = new Date();
    this.cursor.set(new Date(n.getFullYear(), n.getMonth(), 1));
    this.selected.set(isoLocal(n));
  }

  protected choose(cell: CalCell): void {
    if (!cell.visits.length) { return; }
    this.selected.set(cell.iso);
    this.pickDay.emit(cell.visits);
  }

  /**
   * The dot's colour. Same vocabulary the list below uses, on purpose - a
   * client should not have to learn two colour schemes on one page.
   */
  protected tone(v: BookingModel): string {
    switch (v.status) {
      case 'COMPLETED': return 'ok';
      case 'CANCELLED': return 'bad';
      case 'NO_SHOW':   return 'warn';
      default:
        return new Date(v.start).getTime() < Date.now() ? 'warn' : 'next';
    }
  }

  protected timeOf(v: BookingModel): string {
    return new Date(v.start).toLocaleTimeString(undefined,
      { hour: 'numeric', minute: '2-digit' });
  }

  /** What a screen reader should hear on a day square. */
  protected cellLabel(cell: CalCell): string {
    const day = cell.date.toLocaleDateString(undefined,
      { weekday: 'long', day: 'numeric', month: 'long' });
    if (!cell.visits.length) { return `${day}, no visit`; }
    const n = cell.visits.length;
    return `${day}, ${n} ${n === 1 ? 'visit' : 'visits'}`;
  }
}

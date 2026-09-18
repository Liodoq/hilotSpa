import {
  Component, ElementRef, HostListener, computed, effect, inject, input, output, signal,
} from '@angular/core';

/** Local yyyy-MM-dd. Never toISOString() — that is UTC, and in Manila it hands
 *  back yesterday for anything before 8am. Same rule as the queue calendar. */
function iso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function parse(v: string): Date | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(v)) { return null; }
  const [y, m, d] = v.split('-').map(Number);
  const dt = new Date(y, m - 1, d);
  // Rejects 2026-02-31, which Date would silently roll into March.
  return dt.getFullYear() === y && dt.getMonth() === m - 1 && dt.getDate() === d ? dt : null;
}

const DOW = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July',
  'August', 'September', 'October', 'November', 'December'];

interface Cell { key: string; day: number; today: boolean; chosen: boolean; outside: boolean; }

/**
 * A date field in the spa's own vocabulary.
 *
 * `<input type="date">` was the honest first choice and it is why this exists:
 * the FIELD can be styled, but the popup it opens is drawn by the browser and
 * no amount of CSS reaches inside it. On these two screens that produced a
 * white Chrome calendar with blue selection sitting inside a green console —
 * the one piece of chrome on the page that belonged to somebody else.
 *
 * So the calendar is ours. What that costs is everything the native control
 * gave away for free, and each of those is handled here rather than lost:
 * typing a date, keyboard navigation, Escape, click-away, and a value shape the
 * API already speaks (yyyy-MM-dd).
 */
@Component({
  selector: 'app-date-field',
  templateUrl: './date-field.html',
  styleUrl: './date-field.scss',
})
export class DateField {
  private host = inject(ElementRef<HTMLElement>);

  /** yyyy-MM-dd, or '' for no date. */
  value = input<string>('');
  label = input<string>('');
  placeholder = input<string>('Any date');

  readonly valueChange = output<string>();

  protected open = signal(false);
  /** The month on screen, which is not the same thing as the chosen date. */
  protected cursor = signal(new Date());

  constructor() {
    // Opening on a chosen date should show THAT month, not today's. Nudging a
    // range back to last March and being dropped in September every time is the
    // small friction that makes people go back to typing.
    effect(() => {
      const d = parse(this.value());
      if (d) { this.cursor.set(new Date(d.getFullYear(), d.getMonth(), 1)); }
    });
  }

  protected readonly dow = DOW;

  protected monthLabel = computed(() => {
    const c = this.cursor();
    return `${MONTHS[c.getMonth()]} ${c.getFullYear()}`;
  });

  /** What the closed field shows. */
  protected shown = computed(() => {
    const d = parse(this.value());
    if (!d) { return ''; }
    return `${d.getDate()} ${MONTHS[d.getMonth()].slice(0, 3)} ${d.getFullYear()}`;
  });

  /**
   * Six weeks, always.
   *
   * A grid that is five rows in one month and six in the next makes the popup
   * change height as you page through it, and the buttons underneath move out
   * from under the cursor.
   */
  protected weeks = computed<Cell[][]>(() => {
    const c = this.cursor();
    const first = new Date(c.getFullYear(), c.getMonth(), 1);
    const start = new Date(first);
    start.setDate(1 - first.getDay());

    const today = iso(new Date());
    const chosen = this.value();

    const out: Cell[][] = [];
    const walk = new Date(start);
    for (let w = 0; w < 6; w++) {
      const row: Cell[] = [];
      for (let i = 0; i < 7; i++) {
        const key = iso(walk);
        row.push({
          key,
          day: walk.getDate(),
          today: key === today,
          chosen: key === chosen,
          outside: walk.getMonth() !== c.getMonth(),
        });
        walk.setDate(walk.getDate() + 1);
      }
      out.push(row);
    }
    return out;
  });

  protected toggle(): void {
    this.open.update(v => !v);
  }

  protected step(months: number): void {
    const c = this.cursor();
    this.cursor.set(new Date(c.getFullYear(), c.getMonth() + months, 1));
  }

  protected pick(key: string): void {
    this.valueChange.emit(key);
    this.open.set(false);
  }

  protected today(): void {
    this.pick(iso(new Date()));
  }

  protected clear(): void {
    this.valueChange.emit('');
    this.open.set(false);
  }

  /**
   * Typing still works.
   *
   * The native control allowed it and people who use these screens all day will
   * type faster than they can click. Anything unparseable is simply not emitted
   * — no error state, because a half-typed date is not a mistake.
   */
  protected typed(raw: string): void {
    if (raw === '') { this.valueChange.emit(''); return; }
    if (parse(raw)) { this.valueChange.emit(raw); }
  }

  @HostListener('document:click', ['$event'])
  protected onDocClick(e: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(e.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    this.open.set(false);
  }
}

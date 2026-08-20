import { Component, input } from '@angular/core';

@Component({
  selector: 'app-logo',
  template: `
    <span class="ring" [style.width.px]="size()" [style.height.px]="size()">
      <svg viewBox="0 0 48 48" role="img" aria-label="Knead Wellness Spa"
           [style.width.px]="size() * 0.72" [style.height.px]="size() * 0.72">
        <path d="M5 19 C5 33 15 42 24 42 C33 42 43 33 43 19 C36 29 29 32 24 32 C19 32 12 29 5 19Z"
              fill="var(--color-forest-700)" />
        <circle cx="24" cy="17" r="7" fill="var(--color-gold-500)" />
        <path d="M31 11 C36 8 41 9 45 12 C40 15 34 15 31 11Z" fill="var(--color-forest-500)" />
      </svg>
    </span>
  `,
  styles: [`
    .ring { display: inline-flex; align-items: center; justify-content: center;
            border-radius: 50%; background: #fff; border: 1px solid var(--color-line);
            flex: 0 0 auto; overflow: hidden; }
  `],
})
export class Logo {
  size = input(64);
}

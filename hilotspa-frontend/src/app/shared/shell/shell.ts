import { Component, computed, input, output } from '@angular/core';
import { Logo } from '../logo/logo';

/** The chrome every assessment step sits inside: brand bar, progress, footer nav. */
@Component({
  selector: 'app-shell',
  imports: [Logo],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  step = input.required<number>();
  total = input(6);
  backLabel = input('Back');
  nextLabel = input('Continue');
  hint = input('Nothing is saved until you submit on the last step.');
  nextDisabled = input(false);
  wide = input(false);

  back = output<void>();
  next = output<void>();
  exit = output<void>();

  progress = computed(() => (this.step() / this.total()) * 100);
}

import { Injectable, signal } from '@angular/core';

/** One line of feedback after an action. The .toast styling already exists in
 *  interactions.scss; this just owns the message and the timer. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly message = signal<string>('');
  readonly visible = signal(false);
  private handle: ReturnType<typeof setTimeout> | null = null;

  show(msg: string, ms = 2400): void {
    this.message.set(msg);
    this.visible.set(true);
    if (this.handle) clearTimeout(this.handle);
    this.handle = setTimeout(() => this.visible.set(false), ms);
  }
}

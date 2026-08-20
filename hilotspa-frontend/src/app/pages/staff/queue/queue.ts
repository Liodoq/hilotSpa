import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashShell } from '../../../shared/dash-shell/dash-shell';
import { ToastService } from '../../../core/toast.service';
import { DemoQueueItem, QUEUE, ROOMS, STAFF } from '../../../core/demo';

/** S2 — walk-in queue. Named explicitly in Figure 3.2. */
@Component({
  selector: 'app-staff-queue',
  imports: [DashShell, RouterLink],
  templateUrl: './queue.html',
  styleUrl: './queue.scss',
})
export class StaffQueue {
  protected toast = inject(ToastService);
  protected staff = STAFF;
  protected rooms = ROOMS;

  items = signal<DemoQueueItem[]>([...QUEUE]);

  callNext(): void {
    const first = this.items()[0];
    if (!first) return;
    this.items.update(list => list.slice(1));
    this.toast.show(`${first.name} called through to Room 1`);
  }

  moveUp(i: number): void {
    if (i <= 0) return;
    this.items.update(list => {
      const next = [...list];
      [next[i - 1], next[i]] = [next[i], next[i - 1]];
      return next;
    });
    this.toast.show('Moved up the queue');
  }
}

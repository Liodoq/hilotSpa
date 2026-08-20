import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { Toast } from '../../shared/toast/toast';
import { ToastService } from '../../core/toast.service';
import { NEXT_VISIT } from '../../core/demo';

/** C10 — terminal state of Figure 2. priceAtBooking is a snapshot; payment is
 *  PAID_AT_COUNTER, which is how revenue is tracked without processing money. */
@Component({
  selector: 'app-booking',
  imports: [AppNav, Toast, RouterLink],
  templateUrl: './booking.html',
  styleUrl: './booking.scss',
})
export class Booking {
  protected toast = inject(ToastService);
  protected b = NEXT_VISIT;
}

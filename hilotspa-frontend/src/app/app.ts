import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { ProfileStore } from './core/profile.store';
import { BookingStore } from './core/booking.store';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private auth = inject(AuthService);
  private profile = inject(ProfileStore);
  private booking = inject(BookingStore);

  constructor() {
    // The guards are synchronous, so ProfileStore seeds itself from cache and
    // this refreshes it from the server in the background. A stale cache can
    // let someone into the wizard for one navigation; a wrong SERVER row would
    // be a real problem, so the server always wins once it answers.
    if (this.auth.token()) {
      void this.profile.load();
      void this.booking.load();
    }
  }
}

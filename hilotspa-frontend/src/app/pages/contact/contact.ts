import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { PublicApi, PublicSpa } from '../../core/public.api';

/**
 * Contact details, and deliberately not a contact form.
 *
 * A form would need somewhere to send mail from, and this system has no mail
 * path. A form that silently files a message nobody reads is worse than no
 * form: the client believes they have made contact and waits. Every channel on
 * this page is one that actually reaches the spa today.
 *
 * The details come from configuration rather than hardcoded markup, so the
 * trade name can change in one line when the adviser settles §A3 — and so this
 * page tells the truth on the day the phone number is finally filled in.
 */
@Component({
  selector: 'app-contact',
  imports: [AppNav, RouterLink],
  templateUrl: './contact.html',
  styleUrl: './contact.scss',
})
export class Contact implements OnInit {
  private api = inject(PublicApi);
  protected spa = signal<PublicSpa | null>(null);
  protected loading = signal(true);

  /**
   * Whether any way of reaching the spa remotely is published yet.
   *
   * When nothing is, the page says so plainly and points at the door instead of
   * showing empty cards where a phone number should be. An unfilled field is
   * not a design problem to hide; it is a fact the visitor needs.
   */
  protected reachable = computed(() => !!(this.spa()?.phone || this.spa()?.facebook));

  async ngOnInit(): Promise<void> {
    try { this.spa.set(await this.api.spa()); } catch { /* the defaults below cover it */ }
    finally { this.loading.set(false); }
  }
}

import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';
import { Logo } from '../../shared/logo/logo';

/**
 * X3 — where a 403 or a 404 lands.
 *
 * Deliberately the same page for both. The API returns 404 rather than 403 for
 * a record the caller should not know exists, and the UI must not undo that by
 * saying "forbidden" — that would confirm the record is real.
 */
@Component({
  selector: 'app-not-found',
  imports: [AppNav, Logo, RouterLink],
  templateUrl: './not-found.html',
  styleUrl: './not-found.scss',
})
export class NotFound {}

import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppNav } from '../../shared/app-nav/app-nav';

/**
 * About — the page where a stranger decides whether to trust these hands.
 *
 * It used to be three grey cards of disclosures. The disclosures are still
 * here, further down, where they belong; what leads now is the practice
 * itself. The centre of the page is a glossary, because our own menu is
 * written in Tagalog and Spanish loanwords and a client who cannot name
 * Ventosa will not book Ventosa.
 *
 * Every definition says what a treatment IS. None of them claims a cure —
 * the same line the assistant is held to, and it belongs on the page that
 * explains the assistant.
 */
@Component({
  selector: 'app-about',
  imports: [AppNav, RouterLink],
  templateUrl: './about.html',
  styleUrl: './about.scss',
})
export class About {}

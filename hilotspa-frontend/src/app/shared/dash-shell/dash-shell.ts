import { Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { BranchContext } from '../../core/branch-context';
import { Connectivity } from '../../core/connectivity';
import { Logo } from '../logo/logo';
import { Toast } from '../toast/toast';

interface NavItem { path: string; label: string; }

const STAFF_NAV: NavItem[] = [
  { path: '/staff/dashboard', label: 'Dashboard' },
  { path: '/staff/queue',     label: 'Queue' },
  { path: '/staff/resources', label: 'Therapists & rooms' },
  { path: '/staff/report',    label: 'Visit log' },
  { path: '/staff/walkin',    label: 'Record walk-in' },
];
const ADMIN_NAV: NavItem[] = [
  { path: '/admin/overview', label: 'Overview' },
  { path: '/admin/branches', label: 'Branches' },
  { path: '/admin/accounts', label: 'Accounts' },
  { path: '/admin/config',   label: 'Configuration' },
  { path: '/admin/audit',    label: 'Audit logs' },
];

/**
 * The back-office chrome: sidebar, scope badge, optional banner.
 *
 * `role` is the AREA being shown, not who is signed in. An administrator
 * opening a branch is looking at the staff area, and used to lose every admin
 * link the moment they arrived — Figure 3.3 calls that "context switching", and
 * a switch you cannot switch back from is a dead end. When the signed-in user
 * is an ADMIN inside the staff area, both sets of links are shown and the
 * banner says whose context they are in.
 *
 * Every label here is derived from the session. They were hardcoded to
 * "Front desk · Bulan" and "BULAN BRANCH", so a Sorsogon staff account saw
 * Sorsogon's data under a sidebar that said Bulan — the screen contradicting
 * the very claim BranchScopingTest exists to prove.
 */
@Component({
  selector: 'app-dash-shell',
  imports: [RouterLink, RouterLinkActive, Logo, Toast],
  templateUrl: './dash-shell.html',
  styleUrl: './dash-shell.scss',
})
export class DashShell {
  protected auth = inject(AuthService);
  protected net = inject(Connectivity);
  protected ctx = inject(BranchContext);
  private router = inject(Router);

  role = input.required<'STAFF' | 'ADMIN'>();
  scopeNote = input('');
  banner = input<string | null>(null);
  bannerTone = input<'off' | 'ctx'>('off');
  bannerRight = input<string | null>(null);

  /** Account menu. One stray click on the avatar used to sign you out — B48,
   *  fixed in the customer nav and left in place here, which is the shell the
   *  whole demonstration runs in. */
  open = signal(false);

  /** Who is actually signed in, as opposed to which area is on screen. */
  private isAdmin = computed(() => this.auth.role() === 'ADMIN');

  /** An administrator standing in the staff area. */
  protected visiting = computed(() => this.isAdmin() && this.role() === 'STAFF');

  nav = computed<NavItem[]>(() =>
    this.role() === 'STAFF' ? STAFF_NAV : ADMIN_NAV);

  /** Shown under a divider when an administrator is visiting a branch. */
  returnNav = computed<NavItem[]>(() => this.visiting() ? ADMIN_NAV : []);

  /**
   * The way BACK into a branch you are still inside.
   *
   * The template already fixed one half of this: an administrator who opened a
   * branch used to lose every admin link, "and a switch you cannot switch back
   * from is a dead end". The other half was left standing. Clicking Overview
   * does not clear the branch - nothing here calls leave() - but every
   * branch-scoped screen disappears from the sidebar, so it reads exactly as if
   * it had, and the only route back is Branches and choosing again.
   *
   * The branch survives; the sidebar now says so and offers the door.
   */
  /**
   * Set by a screen that already shows the branch state itself.
   *
   * Only Branches does: it marks the open branch "Currently open", scopes a
   * panel to it and offers "Leave branch view". Repeating that in the sidebar
   * put three controls for one thing on one screen, and three ways to do
   * something reads as three different things.
   */
  ownsBranchControls = input(false);

  protected inBranch = computed(() =>
    this.isAdmin() && this.role() === 'ADMIN'
    && this.ctx.active() && !this.ownsBranchControls());

  roleLabel = computed(() => {
    if (this.isAdmin()) {
      if (!this.visiting()) return 'Administrator · all branches';
      const name = this.ctx.name();
      return name ? `Administrator · visiting ${name}` : 'Administrator · visiting a branch';
    }
    const branch = this.auth.branchName();
    return branch ? `Front desk · ${short(branch)}` : 'Front desk';
  });

  scopeLabel = computed(() => {
    if (this.role() === 'ADMIN') return 'ALL BRANCHES';
    // An administrator visiting a branch has no branch of their own, so the name
    // comes from the context they switched into rather than from their token.
    if (this.isAdmin()) {
      const ctxName = this.ctx.name();
      // "BRANCH VIEW" with no branch behind it is the header asserting
      // something the rest of the screen then contradicts. If there is no
      // branch, say so - that is a state, not a failure to load.
      return ctxName ? ctxName.toUpperCase() : 'ALL BRANCHES';
    }
    const branch = this.auth.branchName();
    return branch ? short(branch).toUpperCase() : 'NO BRANCH ASSIGNED';
  });

  initials = computed(() =>
    (this.auth.fullName() || 'K W').split(' ').filter(Boolean)
      .slice(0, 2).map(p => p[0]).join('').toUpperCase());

  go(path: string): void {
    this.open.set(false);
    this.router.navigateByUrl(path);
  }

  /** Clears the branch context as well as navigating — otherwise the next
   *  administrator screen is still quietly scoped to one branch. */
  leaveBranch(): void {
    this.ctx.leave();
    this.go('/admin/overview');
  }

  /**
   * Close the branch without going anywhere.
   *
   * leaveBranch() sends you to Overview, which is right when you are LEAVING a
   * branch-scoped screen - there is nothing left to look at. From an
   * administrator screen you are already somewhere global, so the same jump
   * would move you off the page you were reading for no reason. Closing a scope
   * and navigating are two different intentions and now have two different
   * buttons.
   */
  closeBranch(): void {
    this.open.set(false);
    this.ctx.leave();
  }

  signOut(): void {
    this.open.set(false);
    this.auth.logout();
  }
}

/** "Knead Wellness Spa - Bulan" -> "Bulan". The brand is already in the header. */
function short(branchName: string): string {
  const dash = branchName.lastIndexOf('-');
  return dash === -1 ? branchName : branchName.slice(dash + 1).trim();
}

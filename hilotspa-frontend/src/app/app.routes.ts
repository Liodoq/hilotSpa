import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { roleGuard } from './core/role.guard';
import { profileGuard } from './core/profile.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },

  /* ---- public ---- */
  { path: 'login',    loadComponent: () => import('./pages/login/login').then(m => m.Login) },
  { path: 'register', loadComponent: () => import('./pages/register/register').then(m => m.Register) },

  /* ---- customer ---- */
  { path: 'home',     canActivate: [authGuard], loadComponent: () => import('./pages/home/home').then(m => m.Home) },
  { path: 'services', canActivate: [authGuard], loadComponent: () => import('./pages/services/services').then(m => m.Services) },
  { path: 'book',     canActivate: [authGuard], loadComponent: () => import('./pages/book/book').then(m => m.Book) },
  { path: 'booking',  canActivate: [authGuard], loadComponent: () => import('./pages/booking/booking').then(m => m.Booking) },
  { path: 'profile',  canActivate: [authGuard], loadComponent: () => import('./pages/profile/profile').then(m => m.Profile) },

  /* ---- the pre-assessment wizard: one form across FIVE screens.
         Demographics used to be step 2. They belong to the person rather than
         the visit, so they moved to /profile and profileGuard simply requires
         that they exist. A returning client never sees them again. ---- */
  {
    path: 'assessment',
    canActivate: [authGuard, profileGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'intent' },
      { path: 'intent',       loadComponent: () => import('./pages/assessment/intent/intent').then(m => m.Intent) },
      { path: 'body-map',     loadComponent: () => import('./pages/assessment/body-map-step/body-map-step').then(m => m.BodyMapStep) },
      { path: 'complaints',   loadComponent: () => import('./pages/assessment/complaints/complaints').then(m => m.Complaints) },
      { path: 'history',      loadComponent: () => import('./pages/assessment/history/history').then(m => m.History) },
      { path: 'review',       loadComponent: () => import('./pages/assessment/review/review').then(m => m.Review) },
    ],
  },

  /* ---- branch staff. Figure 3.2: restricted to location-specific data. ---- */
  {
    path: 'staff',
    canActivate: [roleGuard('STAFF', 'ADMIN')],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadComponent: () => import('./pages/staff/dashboard/dashboard').then(m => m.StaffDashboard) },
      { path: 'queue',     loadComponent: () => import('./pages/staff/queue/queue').then(m => m.StaffQueue) },
      { path: 'resources', loadComponent: () => import('./pages/staff/resources/resources').then(m => m.StaffResources) },
      { path: 'report',    loadComponent: () => import('./pages/staff/report/report').then(m => m.StaffReport) },
      { path: 'walkin',    loadComponent: () => import('./pages/staff/walkin/walkin').then(m => m.StaffWalkin) },
    ],
  },

  /* ---- administrator. Figure 3.3: node aggregation + context switching. ---- */
  {
    path: 'admin',
    canActivate: [roleGuard('ADMIN')],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      { path: 'overview', loadComponent: () => import('./pages/admin/overview/overview').then(m => m.AdminOverview) },
      { path: 'branches', loadComponent: () => import('./pages/admin/branches/branches').then(m => m.AdminBranches) },
      { path: 'accounts', loadComponent: () => import('./pages/admin/accounts/accounts').then(m => m.AdminAccounts) },
      { path: 'config',   loadComponent: () => import('./pages/admin/config/config').then(m => m.AdminConfig) },
      { path: 'audit',    loadComponent: () => import('./pages/admin/audit/audit').then(m => m.AdminAudit) },
    ],
  },

  /* ---- X3. The same page answers 403 and 404 on purpose: the API returns 404
         for a record you should not know exists, and the UI must not undo that. ---- */
  { path: 'not-found', loadComponent: () => import('./pages/not-found/not-found').then(m => m.NotFound) },
  { path: '**', redirectTo: 'not-found' },
];

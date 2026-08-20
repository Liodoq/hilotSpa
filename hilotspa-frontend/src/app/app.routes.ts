import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'login' },

  { path: 'login',    loadComponent: () => import('./pages/login/login').then(m => m.Login) },
  { path: 'register', loadComponent: () => import('./pages/register/register').then(m => m.Register) },

  {
    path: 'assessment',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'intent' },
      { path: 'intent',       loadComponent: () => import('./pages/assessment/intent/intent').then(m => m.Intent) },
      { path: 'demographics', loadComponent: () => import('./pages/assessment/demographics/demographics').then(m => m.Demographics) },
      { path: 'body-map',     loadComponent: () => import('./pages/assessment/body-map-step/body-map-step').then(m => m.BodyMapStep) },
      { path: 'complaints',   loadComponent: () => import('./pages/assessment/complaints/complaints').then(m => m.Complaints) },
      { path: 'history',      loadComponent: () => import('./pages/assessment/history/history').then(m => m.History) },
      { path: 'review',       loadComponent: () => import('./pages/assessment/review/review').then(m => m.Review) },
    ],
  },

  { path: '**', redirectTo: 'login' },
];

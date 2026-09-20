import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { treasurerGuard } from './core/treasurer.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./public-overview/public-overview').then((m) => m.PublicOverview),
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./dashboard/dashboard').then((m) => m.Dashboard),
    canActivate: [authGuard],
  },
  {
    path: 'collections/:id',
    loadComponent: () => import('./collection-details/collection-details').then((m) => m.CollectionDetails),
    canActivate: [authGuard],
  },
  {
    path: 'students/:id',
    loadComponent: () => import('./student-view/student-view').then((m) => m.StudentView),
    canActivate: [authGuard],
  },
  {
    path: 'treasurer',
    loadComponent: () => import('./treasurer-panel/treasurer-panel').then((m) => m.TreasurerPanel),
    canActivate: [authGuard, treasurerGuard],
  },
  {
    path: 'ledger',
    loadComponent: () => import('./global-ledger/global-ledger').then((m) => m.GlobalLedger),
    canActivate: [authGuard, treasurerGuard],
  },
  {
    path: 'login',
    loadComponent: () => import('./login/login').then((m) => m.Login),
  },
  {
    path: 'jak-to-dziala',
    loadComponent: () => import('./how-it-works/how-it-works').then((m) => m.HowItWorks),
  },
];

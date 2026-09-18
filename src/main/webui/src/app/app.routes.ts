import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

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
    path: 'parents/:id',
    loadComponent: () => import('./parent-view/parent-view').then((m) => m.ParentView),
    canActivate: [authGuard],
  },
  {
    path: 'treasurer',
    loadComponent: () => import('./treasurer-panel/treasurer-panel').then((m) => m.TreasurerPanel),
    canActivate: [authGuard],
  },
  {
    path: 'ledger',
    loadComponent: () => import('./global-ledger/global-ledger').then((m) => m.GlobalLedger),
    canActivate: [authGuard],
  },
  {
    path: 'login',
    loadComponent: () => import('./login/login').then((m) => m.Login),
  },
];

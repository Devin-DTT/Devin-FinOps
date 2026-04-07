import { Routes } from '@angular/router';
import { DashboardComponent } from './features/dashboard/dashboard.component';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'sessions', loadChildren: () => import('./features/sessions/sessions.routes').then(m => m.SESSIONS_ROUTES) },
  { path: 'billing', loadChildren: () => import('./features/billing/billing.routes').then(m => m.BILLING_ROUTES) },
  { path: 'metrics', loadChildren: () => import('./features/metrics/metrics.routes').then(m => m.METRICS_ROUTES) },
  { path: 'admin', loadChildren: () => import('./features/admin/admin.routes').then(m => m.ADMIN_ROUTES) },
  { path: '**', redirectTo: 'dashboard' }
];

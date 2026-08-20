import { Routes } from '@angular/router';
import { authGuard, adminGuard, guestGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full',
  },

  // ── Public ───────────────────────────────────────────────
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'signup',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/signup/signup.component').then(m => m.SignupComponent),
  },

  // ── Protected ────────────────────────────────────────────
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./app-shell/app-shell.component').then(m => m.AppShellComponent),
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/booking/availability/availability.component')
            .then(m => m.AvailabilityComponent),
      },
      {
        path: 'my-bookings',
        loadComponent: () =>
          import('./features/booking/my-bookings/my-bookings.component')
            .then(m => m.MyBookingsComponent),
      },
      {
        path: 'waitlist',
        loadComponent: () =>
          import('./features/booking/waitlist/waitlist.component')
            .then(m => m.WaitlistComponent),
      },
      {
        path: 'plans',
        loadComponent: () =>
          import('./features/plans/plans.component').then(m => m.PlansComponent),
      },
      {
        path: 'subscription',
        loadComponent: () =>
          import('./features/plans/subscription/subscription.component')
            .then(m => m.SubscriptionComponent),
      },
      {
        path: 'checkin',
        loadComponent: () =>
          import('./features/checkin/checkin.component').then(m => m.CheckinComponent),
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/profile/profile.component').then(m => m.ProfileComponent),
      },
      // ── Admin ───────────────────────────────────────────
      {
        path: 'admin',
        canActivate: [adminGuard],
        children: [
          {
            path: 'members',
            loadComponent: () =>
              import('./features/admin/members/members.component')
                .then(m => m.MembersComponent),
          },
          {
            path: 'plans',
            loadComponent: () =>
              import('./features/admin/plans/admin-plans.component')
                .then(m => m.AdminPlansComponent),
          },
          {
            path: 'partners',
            loadComponent: () =>
              import('./features/admin/partners/admin-partners.component')
                .then(m => m.AdminPartnersComponent),
          },
          {
            path: 'payments',
            loadComponent: () =>
              import('./features/admin/payments/admin-payments.component')
                .then(m => m.AdminPaymentsComponent),
          },
          { path: '', redirectTo: 'members', pathMatch: 'full' },
        ],
      },
    ],
  },

  { path: '**', redirectTo: 'dashboard' },
];

import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { IconComponent, IconName } from '../shared/icon.component';
import { ToastHostComponent } from '../shared/toast-host.component';

interface NavItem { label: string; icon: IconName; route: string; adminOnly?: boolean; }

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, ToastHostComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="app-layout">
      @if (sidebarOpen()) {
        <div class="scrim" (click)="sidebarOpen.set(false)"></div>
      }

      <nav class="sidebar" [class.open]="sidebarOpen()">
        <div class="brand">
          <span class="brand-mark"><app-icon name="dumbbell" [size]="19" /></span>
          <span class="brand-name">GymSystem</span>
        </div>

        @if (tenant()) {
          <div class="tenant">
            <span class="dot dot-pulse" style="color:var(--accent)"></span>
            <span class="truncate">{{ tenant() }}</span>
          </div>
        }

        <ul class="nav">
          @for (item of memberNav(); track item.route) {
            <li>
              <a [routerLink]="item.route" routerLinkActive="active" (click)="sidebarOpen.set(false)">
                <app-icon [name]="item.icon" [size]="17" />{{ item.label }}
              </a>
            </li>
          }

          @if (isAdmin()) {
            <li class="nav-group">Administração</li>
            @for (item of adminNav(); track item.route) {
              <li>
                <a [routerLink]="item.route" routerLinkActive="active" (click)="sidebarOpen.set(false)">
                  <app-icon [name]="item.icon" [size]="17" />{{ item.label }}
                </a>
              </li>
            }
          }
        </ul>

        <div class="foot">
          <div class="who">
            <span class="who-email truncate">{{ userEmail() }}</span>
            <span class="badge badge-accent">{{ roleLabel() }}</span>
          </div>
          <button class="btn btn-ghost btn-sm btn-full" (click)="logout()">
            <app-icon name="logout" [size]="15" />Sair
          </button>
        </div>
      </nav>

      <main class="main-content">
        <button class="menu-btn btn btn-secondary btn-sm" (click)="sidebarOpen.set(true)" aria-label="Abrir menu">
          <app-icon name="menu" [size]="18" />
        </button>
        <router-outlet />
      </main>

      <app-toast-host />
    </div>
  `,
  styles: [`
    .scrim { position: fixed; inset: 0; z-index: 99; background: rgba(4,6,9,.7); backdrop-filter: blur(2px); }

    .brand { display: flex; align-items: center; gap: 10px; padding: 22px 20px 18px; font-size: 16px; font-weight: 800; letter-spacing: -.02em; }
    .brand-mark { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 9px; background: var(--accent); color: var(--accent-ink); }

    .tenant { display: flex; align-items: center; gap: 8px; margin: 0 14px 14px; padding: 8px 12px; background: var(--surface-hi); border: 1px solid var(--border); border-radius: var(--r); font-size: 13px; font-weight: 600; color: var(--text-muted); }

    .nav { list-style: none; margin: 0; padding: 0 12px; flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 2px; }
    .nav a { display: flex; align-items: center; gap: 11px; padding: 10px 12px; border-radius: var(--r-sm); color: var(--text-muted); font-size: 14px; font-weight: 500; transition: background var(--t-fast), color var(--t-fast); }
    .nav a:hover { background: var(--surface-hi); color: var(--text); }
    .nav a.active { background: var(--accent-soft); color: var(--accent); font-weight: 600; }
    .nav-group { margin: 16px 12px 6px; font-size: 11px; font-weight: 700; letter-spacing: .1em; text-transform: uppercase; color: var(--text-dim); }

    .foot { padding: 14px; border-top: 1px solid var(--border); display: flex; flex-direction: column; gap: 10px; }
    .who { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-width: 0; }
    .who-email { font-size: 12px; color: var(--text-muted); }

    .menu-btn { display: none; margin-bottom: 16px; padding: 8px; }
    @media (max-width: 900px) { .menu-btn { display: inline-flex; } }
  `],
})
export class AppShellComponent {
  private auth = inject(AuthService);

  sidebarOpen = signal(false);

  private readonly MEMBER: NavItem[] = [
    { label: 'Agenda',         icon: 'calendar', route: '/dashboard'    },
    { label: 'Minhas aulas',   icon: 'ticket',   route: '/my-bookings'  },
    { label: 'Fila de espera', icon: 'clock',    route: '/waitlist'     },
    { label: 'Check-in',       icon: 'check',    route: '/checkin'      },
    { label: 'Planos',         icon: 'tag',      route: '/plans'        },
    { label: 'Assinatura',     icon: 'receipt',  route: '/subscription' },
    { label: 'Perfil',         icon: 'user',     route: '/profile'      },
  ];

  private readonly ADMIN: NavItem[] = [
    { label: 'Membros',    icon: 'users', route: '/admin/members'  },
    { label: 'Planos',     icon: 'card',  route: '/admin/plans'    },
    { label: 'Parceiros',   icon: 'check', route: '/admin/partners' },
    { label: 'Assinaturas', icon: 'money', route: '/admin/payments' },
  ];

  memberNav = computed(() => this.MEMBER);
  adminNav  = computed(() => this.ADMIN);

  tenant    = computed(() => this.auth.tenant());
  userEmail = computed(() => this.auth.currentUser()?.sub ?? '');
  isAdmin   = computed(() => this.auth.isAdmin());

  roleLabel = computed(() => {
    const r = this.auth.tenantRole() ?? this.auth.currentUser()?.role ?? '';
    const map: Record<string, string> = {
      OWNER: 'Dono', MANAGER: 'Gerente', STAFF: 'Recepção',
      TRAINER: 'Professor', MEMBER: 'Aluno',
      ADMIN_WEB: 'Admin', ADMIN_APP: 'Admin', USER: 'Aluno',
    };
    return map[r] ?? r;
  });

  logout() { this.auth.logout(); }
}

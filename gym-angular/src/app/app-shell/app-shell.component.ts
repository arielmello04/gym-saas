import { Component, signal, computed } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../core/services/auth.service';

interface NavItem { label: string; icon: string; route: string; adminOnly?: boolean; }

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="app-layout">
      <div class="overlay" [class.visible]="sidebarOpen()" (click)="sidebarOpen.set(false)"></div>

      <nav class="sidebar" [class.open]="sidebarOpen()">
        <div class="sidebar-brand">
          <span>💪</span><span class="brand-name">GymSystem</span>
        </div>

        @if (tenant()) {
          <div class="tenant-badge">
            <span class="tenant-dot"></span>{{ tenant() }}
          </div>
        }

        <ul class="nav-list">
          @for (item of visibleNav(); track item.route) {
            <li>
              <a [routerLink]="item.route" routerLinkActive="active" (click)="sidebarOpen.set(false)">
                <span class="nav-icon">{{ item.icon }}</span>{{ item.label }}
              </a>
            </li>
          }
        </ul>

        <div class="sidebar-footer">
          <div class="user-info">
            <span class="user-email">{{ userEmail() }}</span>
            <span class="badge badge-info" style="font-size:11px">{{ roleLabel() }}</span>
          </div>
          <button class="btn btn-secondary btn-sm" (click)="logout()">Sair</button>
        </div>
      </nav>

      <main class="main-content">
        <div class="topbar">
          <button class="menu-btn" (click)="sidebarOpen.set(!sidebarOpen())">☰</button>
        </div>
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    .overlay { display:none; position:fixed; inset:0; background:rgba(0,0,0,.4); z-index:99; &.visible{display:block;} }
    .sidebar-brand { display:flex; align-items:center; gap:10px; padding:0 20px 20px; font-size:18px; font-weight:700; border-bottom:1px solid var(--color-border); }
    .tenant-badge { display:flex; align-items:center; gap:8px; padding:10px 20px; font-size:12px; color:var(--color-text-muted); background:var(--color-bg); }
    .tenant-dot { width:8px; height:8px; border-radius:50%; background:var(--color-success); }
    .nav-list { list-style:none; margin:16px 0 0; padding:0; flex:1;
      a { display:flex; align-items:center; gap:10px; padding:10px 20px; color:var(--color-text-muted); font-size:14px; font-weight:500; transition:all var(--transition); text-decoration:none;
        &:hover { background:var(--color-bg); color:var(--color-text); }
        &.active { background:#eef2ff; color:var(--color-primary); border-left:3px solid var(--color-primary); }
      }
      .nav-icon { font-size:16px; width:20px; text-align:center; }
    }
    .sidebar-footer { padding:16px 20px; border-top:1px solid var(--color-border); display:flex; flex-direction:column; gap:10px; }
    .user-email { font-size:12px; font-weight:500; word-break:break-all; }
    .topbar { display:none; padding:12px 0 20px;
      .menu-btn { background:none; border:none; font-size:22px; cursor:pointer; padding:4px 8px; border-radius:6px; }
    }
    @media (max-width:768px) { .topbar { display:flex; } }
  `],
})
export class AppShellComponent {
  sidebarOpen = signal(false);

  private readonly NAV: NavItem[] = [
    { label:'Agenda',         icon:'📅', route:'/dashboard'    },
    { label:'Minhas Aulas',   icon:'🎽', route:'/my-bookings'  },
    { label:'Fila de Espera', icon:'⏳', route:'/waitlist'     },
    { label:'Planos',         icon:'💳', route:'/plans'        },
    { label:'Assinatura',     icon:'📄', route:'/subscription' },
    { label:'Check-in',       icon:'✅', route:'/checkin'      },
    { label:'Perfil',         icon:'👤', route:'/profile'      },
    { label:'Membros',        icon:'👥', route:'/admin/members',  adminOnly:true },
    { label:'Pagamentos',     icon:'💰', route:'/admin/payments', adminOnly:true },
  ];

  constructor(private auth: AuthService) {}

  tenant    = computed(() => this.auth.tenant());
  userEmail = computed(() => this.auth.currentUser()?.sub ?? '');
  isAdmin   = computed(() => this.auth.isAdmin());
  roleLabel = computed(() => {
    const r = this.auth.tenantRole() ?? this.auth.currentUser()?.role ?? '';
    const map: Record<string,string> = { OWNER:'Owner', MANAGER:'Manager', STAFF:'Staff', TRAINER:'Trainer', MEMBER:'Member', ADMIN:'Admin' };
    return map[r] ?? r;
  });
  visibleNav = computed(() => this.NAV.filter(n => !n.adminOnly || this.isAdmin()));
  logout() { this.auth.logout(); }
}

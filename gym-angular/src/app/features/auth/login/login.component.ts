import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-card card">
        <div class="auth-logo">💪 GymSystem</div>
        <h1>Entrar</h1>

        @if (error()) {
          <div class="alert alert-danger">{{ error() }}</div>
        }

        <div class="form-group">
          <label>Academia (tenant)</label>
          <input [(ngModel)]="tenantSlug" placeholder="ex: academia-fit" autocomplete="organization">
        </div>
        <div class="form-group">
          <label>E-mail</label>
          <input [(ngModel)]="email" type="email" placeholder="seu@email.com" autocomplete="email">
        </div>
        <div class="form-group">
          <label>Senha</label>
          <input [(ngModel)]="password" type="password" placeholder="••••••••" autocomplete="current-password">
        </div>

        <button class="btn btn-primary btn-full" (click)="submit()" [disabled]="loading()">
          @if (loading()) { <span class="spinner" style="width:16px;height:16px"></span> }
          Entrar
        </button>

        <p class="auth-link">Não tem conta? <a routerLink="/signup">Criar conta</a></p>
      </div>
    </div>
  `,
  styles: [`
    .auth-page { min-height:100vh; display:flex; align-items:center; justify-content:center; background:var(--color-bg); }
    .auth-card { width:100%; max-width:420px; .auth-logo{font-size:22px;font-weight:700;margin-bottom:8px;} h1{margin:0 0 24px;font-size:20px;} }
    .alert { padding:10px 14px; border-radius:var(--radius); margin-bottom:16px; font-size:14px; &.alert-danger{background:#fee2e2;color:#991b1b;} }
    .auth-link { text-align:center; margin-top:16px; font-size:14px; color:var(--color-text-muted); }
  `],
})
export class LoginComponent {
  tenantSlug = '';
  email      = '';
  password   = '';
  loading    = signal(false);
  error      = signal('');

  constructor(private http: HttpClient, private router: Router) {}

  submit() {
    if (!this.tenantSlug || !this.email || !this.password) { this.error.set('Preencha todos os campos'); return; }
    localStorage.setItem('gym_tenant', this.tenantSlug.trim().toLowerCase());
    this.loading.set(true); this.error.set('');
    this.http.post<{ accessToken: string }>('/api/v1/auth/login', { email: this.email, password: this.password },
  { headers: { 'X-Tenant-ID': this.tenantSlug.trim().toLowerCase() } }
).subscribe({
  next: (res) => { localStorage.setItem('gym_token', res.accessToken); this.router.navigate(['/dashboard']); },
      error: (e: { error?: { message?: string } }) => { this.loading.set(false); this.error.set(e.error?.message ?? 'E-mail ou senha incorretos'); },
    });
  }
}

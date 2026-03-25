import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { SignupRequest } from '../../../core/models';

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="auth-page">
      <div class="auth-card card">
        <div class="auth-logo">💪 GymSystem</div>
        <h1>Criar conta</h1>

        @if (error()) { <div class="alert alert-danger">{{ error() }}</div> }

        <div class="form-group"><label>Academia (tenant)</label><input [(ngModel)]="tenantSlug" placeholder="ex: academia-fit"></div>
        <div class="form-group"><label>E-mail</label><input [(ngModel)]="email" type="email" placeholder="seu@email.com"></div>
        <div class="form-group"><label>Senha</label><input [(ngModel)]="password" type="password" placeholder="Mínimo 8 caracteres"></div>
        <div class="form-group"><label>Token de convite</label><input [(ngModel)]="inviteToken" placeholder="Código fornecido pela academia"></div>

        <button class="btn btn-primary btn-full" (click)="submit()" [disabled]="loading()">
          @if (loading()) { <span class="spinner" style="width:16px;height:16px"></span> }
          Criar conta
        </button>
        <p class="auth-link">Já tem conta? <a routerLink="/login">Entrar</a></p>
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
export class SignupComponent {
  tenantSlug  = ''; email = ''; password = ''; inviteToken = '';
  loading = signal(false); error = signal('');

  constructor(private auth: AuthService, private router: Router) {}

  submit() {
    if (!this.tenantSlug || !this.email || !this.password || !this.inviteToken) { this.error.set('Preencha todos os campos'); return; }
    const tenant = this.tenantSlug.trim().toLowerCase();
    this.auth.setTenant(tenant);
    this.loading.set(true);
    const req: SignupRequest = { email: this.email, password: this.password, inviteToken: this.inviteToken };
    this.auth.signup(req).subscribe({
      next:  () => this.router.navigate(['/dashboard']),
      error: (e: { error?: { message?: string } }) => { this.loading.set(false); this.error.set(e.error?.message ?? 'Erro ao criar conta'); },
    });
  }
}

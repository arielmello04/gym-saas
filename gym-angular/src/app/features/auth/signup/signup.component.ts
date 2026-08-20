import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { IconComponent } from '../../../shared/icon.component';

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [FormsModule, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="auth-page">
      <div class="auth-card enter">
        <div class="brand">
          <span class="brand-mark"><app-icon name="dumbbell" [size]="20" /></span>
          <span>GymSystem</span>
        </div>

        <h1>Criar conta</h1>
        <p class="muted lead">O cadastro é por convite da academia.</p>

        @if (error()) { <div class="alert alert-danger">{{ error() }}</div> }

        <form (ngSubmit)="submit()">
          <div class="form-group">
            <label for="tenant">Academia</label>
            <input id="tenant" name="tenant" [(ngModel)]="tenantSlug"
                   placeholder="academia-fit" autocapitalize="none">
          </div>
          <div class="form-group">
            <label for="invite">Código de convite</label>
            <input id="invite" name="invite" [(ngModel)]="inviteToken" placeholder="AOE0Q0H8I6">
            <span class="field-hint">Peça o código na recepção da academia.</span>
          </div>
          <div class="form-group">
            <label for="email">E-mail</label>
            <input id="email" name="email" [(ngModel)]="email" type="email"
                   placeholder="voce@email.com" autocomplete="email">
          </div>
          <div class="form-group">
            <label for="password">Senha</label>
            <input id="password" name="password" [(ngModel)]="password" type="password"
                   placeholder="mínimo 8 caracteres" autocomplete="new-password">
          </div>

          <button class="btn btn-primary btn-full" type="submit" [disabled]="loading()">
            @if (loading()) { <span class="spinner spinner-sm"></span> }
            Criar conta
          </button>
        </form>

        <p class="foot muted">Já tem conta? <a routerLink="/login">Entrar</a></p>
      </div>
    </div>
  `,
  styles: [`
    .auth-page { min-height: 100vh; display: grid; place-items: center; padding: 24px; }
    .auth-card { width: 100%; max-width: 410px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--r-lg); padding: 30px; box-shadow: var(--shadow-md); }
    .brand { display: flex; align-items: center; gap: 10px; margin-bottom: 26px; font-size: 15px; font-weight: 800; }
    .brand-mark { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 10px; background: var(--accent); color: var(--accent-ink); }
    .lead { font-size: 14px; margin-bottom: 24px; }
    h1 { margin-bottom: 4px; }
    .foot { text-align: center; margin-top: 20px; font-size: 14px; }
  `],
})
export class SignupComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  tenantSlug = '';
  inviteToken = '';
  email = '';
  password = '';
  loading = signal(false);
  error = signal('');

  submit() {
    if (!this.tenantSlug.trim() || !this.inviteToken.trim() || !this.email.trim() || !this.password) {
      this.error.set('Preencha todos os campos.');
      return;
    }
    this.auth.setTenant(this.tenantSlug.trim().toLowerCase());
    this.loading.set(true);
    this.error.set('');

    this.auth.signup({
      email: this.email.trim(),
      password: this.password,
      inviteToken: this.inviteToken.trim(),
    }).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (e: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(e.error?.message ?? 'Não foi possível criar a conta. Confira o convite.');
      },
    });
  }
}

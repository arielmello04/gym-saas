import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { IconComponent } from '../../../shared/icon.component';

@Component({
  selector: 'app-login',
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

        <h1>Entrar</h1>
        <p class="muted lead">Acesse o painel da sua academia.</p>

        @if (error()) { <div class="alert alert-danger">{{ error() }}</div> }

        <form (ngSubmit)="submit()">
          <div class="form-group">
            <label for="tenant">Academia</label>
            <input id="tenant" name="tenant" [(ngModel)]="tenantSlug"
                   placeholder="academia-fit" autocomplete="organization" autocapitalize="none">
            <span class="field-hint">O identificador da academia, como aparece no endereço.</span>
          </div>
          <div class="form-group">
            <label for="email">E-mail</label>
            <input id="email" name="email" [(ngModel)]="email" type="email"
                   placeholder="voce@email.com" autocomplete="email">
          </div>
          <div class="form-group">
            <label for="password">Senha</label>
            <input id="password" name="password" [(ngModel)]="password" type="password"
                   placeholder="••••••••" autocomplete="current-password">
          </div>

          <button class="btn btn-primary btn-full" type="submit" [disabled]="loading()">
            @if (loading()) { <span class="spinner spinner-sm"></span> }
            Entrar
          </button>
        </form>

        <p class="foot muted">Não tem conta? <a routerLink="/signup">Criar com convite</a></p>
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
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  tenantSlug = '';
  email = '';
  password = '';
  loading = signal(false);
  error = signal('');

  submit() {
    if (!this.tenantSlug.trim() || !this.email.trim() || !this.password) {
      this.error.set('Preencha academia, e-mail e senha.');
      return;
    }
    this.auth.setTenant(this.tenantSlug.trim().toLowerCase());
    this.loading.set(true);
    this.error.set('');

    this.auth.login({ email: this.email.trim(), password: this.password }).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (e: { status?: number; error?: { message?: string } }) => {
        this.loading.set(false);
        // 403 aqui quase sempre é academia errada, não senha errada.
        this.error.set(e.status === 403
          ? 'Você não tem vínculo com esta academia.'
          : e.error?.message ?? 'E-mail ou senha incorretos.');
      },
    });
  }
}

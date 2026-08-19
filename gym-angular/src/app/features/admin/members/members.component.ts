import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TenantApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { TenantMember } from '../../../core/models';
import { IconComponent } from '../../../shared/icon.component';

@Component({
  selector: 'app-members',
  standalone: true,
  imports: [DatePipe, FormsModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="page-title">
        <h1>Membros</h1>
        <span class="page-subtitle">Quem tem acesso a esta academia.</span>
      </div>
      <span class="badge badge-neutral nums">{{ members().length }} no total</span>
    </div>

    <div class="card add">
      <h3>Adicionar membro</h3>
      <p class="muted hint">O e-mail precisa ser de uma conta já cadastrada no sistema.</p>
      <div class="add-form">
        <input [(ngModel)]="newEmail" name="email" type="email" placeholder="email@exemplo.com"
               (keyup.enter)="add()">
        <select [(ngModel)]="newRole" name="role" aria-label="Papel">
          @for (r of roles; track r.value) { <option [value]="r.value">{{ r.label }}</option> }
        </select>
        <button class="btn btn-primary" [disabled]="adding()" (click)="add()">
          @if (adding()) { <span class="spinner spinner-sm"></span> } @else { <app-icon name="plus" [size]="16" /> }
          Adicionar
        </button>
      </div>
    </div>

    @if (loading()) {
      <div class="rows">
        @for (i of [1,2,3]; track i) { <div class="skeleton" style="height:58px"></div> }
      </div>
    } @else if (members().length === 0) {
      <div class="empty-state">
        <div class="icon"><app-icon name="users" [size]="40" [stroke]="1.4" /></div>
        <h3>Nenhum membro ainda</h3>
        <p>Adicione pessoas pelo formulário acima ou gere um convite para autocadastro.</p>
      </div>
    } @else {
      <div class="rows">
        @for (m of members(); track m.userId) {
          <div class="row enter">
            <div class="row-main">
              <span class="row-title truncate">{{ m.email }}</span>
              <span class="row-sub nums">Entrou em {{ m.joinedAt | date:'dd/MM/yyyy' }}</span>
            </div>
            <div class="row-side">
              <span class="badge" [class]="m.role === 'OWNER' ? 'badge-accent' : 'badge-neutral'">
                {{ roleLabel(m.role) }}
              </span>
              @if (m.role !== 'OWNER') {
                <button class="btn btn-danger btn-sm" [disabled]="removing() === m.userId" (click)="confirmRemove(m)">
                  @if (removing() === m.userId) { <span class="spinner spinner-sm"></span> }
                  Remover
                </button>
              }
            </div>
          </div>
        }
      </div>
    }

    @if (toRemove(); as m) {
      <div class="modal-overlay" (click)="toRemove.set(null)">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>Remover membro?</h3>
          <p class="muted">{{ m.email }} perde o acesso a esta academia. As reservas e o histórico dele continuam registrados.</p>
          <div class="inline" style="margin-top:20px;justify-content:flex-end">
            <button class="btn btn-secondary btn-sm" (click)="toRemove.set(null)">Manter</button>
            <button class="btn btn-danger btn-sm" (click)="remove(m)">Remover</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .add { margin-bottom: 22px; }
    .add h3 { margin-bottom: 4px; }
    .hint { font-size: 13px; margin-bottom: 14px; }
    .add-form { display: flex; gap: 10px; flex-wrap: wrap; }
    .add-form input { flex: 1; min-width: 220px; }
    .add-form select { width: auto; min-width: 150px; }
  `],
})
export class MembersComponent implements OnInit {
  private api = inject(TenantApiService);
  private toast = inject(ToastService);

  readonly roles = [
    { value: 'MEMBER',  label: 'Aluno' },
    { value: 'TRAINER', label: 'Professor' },
    { value: 'STAFF',   label: 'Recepção' },
    { value: 'MANAGER', label: 'Gerente' },
  ];

  members = signal<TenantMember[]>([]);
  loading = signal(true);
  adding = signal(false);
  removing = signal<number | null>(null);
  toRemove = signal<TenantMember | null>(null);

  newEmail = '';
  newRole = 'MEMBER';

  ngOnInit() { this.load(); }

  roleLabel(role: string) {
    return this.roles.find(r => r.value === role)?.label
      ?? (role === 'OWNER' ? 'Dono' : role);
  }

  add() {
    const email = this.newEmail.trim();
    if (!email) { this.toast.error('Informe o e-mail do membro.'); return; }

    this.adding.set(true);
    this.api.addMember(email, this.newRole).subscribe({
      next: () => {
        this.adding.set(false);
        this.newEmail = '';
        this.toast.success(`${email} adicionado.`);
        this.load();
      },
      error: e => {
        this.adding.set(false);
        this.toast.fromApi(e, 'Não foi possível adicionar o membro.');
      },
    });
  }

  confirmRemove(m: TenantMember) { this.toRemove.set(m); }

  remove(m: TenantMember) {
    this.toRemove.set(null);
    this.removing.set(m.userId);
    this.api.removeMember(m.userId).subscribe({
      next: () => {
        this.removing.set(null);
        this.toast.success(`${m.email} removido.`);
        this.load();
      },
      error: e => {
        this.removing.set(null);
        this.toast.fromApi(e, 'Não foi possível remover o membro.');
      },
    });
  }

  private load() {
    this.loading.set(true);
    this.api.members().subscribe({
      next: m => { this.members.set(m); this.loading.set(false); },
      error: e => {
        this.loading.set(false);
        this.toast.fromApi(e, 'Não foi possível carregar os membros.');
      },
    });
  }
}

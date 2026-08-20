import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PlansApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { Plan } from '../../../core/models';
import { IconComponent } from '../../../shared/icon.component';

@Component({
  selector: 'app-admin-plans',
  standalone: true,
  imports: [FormsModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="page-title">
        <h1>Planos</h1>
        <span class="page-subtitle">O preço cobrado sai daqui, não do aplicativo do aluno.</span>
      </div>
      <button class="btn btn-primary btn-sm" (click)="openNew()">
        <app-icon name="plus" [size]="16" />Novo plano
      </button>
    </div>

    @if (loading()) {
      <div class="rows">
        @for (i of [1,2,3]; track i) { <div class="skeleton" style="height:70px"></div> }
      </div>
    } @else if (plans().length === 0) {
      <div class="empty-state">
        <div class="icon"><app-icon name="card" [size]="40" [stroke]="1.4" /></div>
        <h3>Nenhum plano cadastrado</h3>
        <p>Sem plano no catálogo, os alunos não conseguem assinar.</p>
        <button class="btn btn-primary" (click)="openNew()">Criar primeiro plano</button>
      </div>
    } @else {
      <div class="rows">
        @for (p of plans(); track p.id) {
          <div class="row enter" [class.off]="!p.active">
            <div class="row-main">
              <span class="row-title">{{ p.name }} <span class="dim code">{{ p.code }}</span></span>
              <span class="row-sub nums">R$ {{ money(p.priceReais) }} / {{ p.intervalLabel }}</span>
            </div>
            <div class="row-side">
              <span class="badge" [class]="p.active ? 'badge-success' : 'badge-neutral'">
                {{ p.active ? 'Ativo' : 'Inativo' }}
              </span>
              <button class="btn btn-secondary btn-sm" (click)="openEdit(p)">Editar</button>
              @if (p.active) {
                <button class="btn btn-danger btn-sm" [disabled]="busy() === p.id" (click)="deactivate(p)">
                  @if (busy() === p.id) { <span class="spinner spinner-sm"></span> }
                  Desativar
                </button>
              }
            </div>
          </div>
        }
      </div>
    }

    @if (editing()) {
      <div class="modal-overlay" (click)="close()">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>{{ isNew() ? 'Novo plano' : 'Editar plano' }}</h3>

          @if (isNew()) {
            <div class="form-group">
              <label for="code">Código</label>
              <input id="code" name="code" [(ngModel)]="form.code" placeholder="MENSAL" autocapitalize="characters">
              <span class="field-hint">Identificador fixo, único nesta academia. Não muda depois.</span>
            </div>
          }

          <div class="form-group">
            <label for="name">Nome</label>
            <input id="name" name="name" [(ngModel)]="form.name" placeholder="Mensal">
          </div>

          <div class="form-group">
            <label for="desc">Descrição</label>
            <input id="desc" name="desc" [(ngModel)]="form.description" placeholder="Acesso livre às aulas.">
          </div>

          <div class="two">
            <div class="form-group">
              <label for="price">Preço (R$)</label>
              <input id="price" name="price" [(ngModel)]="form.priceReais" type="number" min="0" step="0.01">
            </div>
            <div class="form-group">
              <label for="months">Ciclo (meses)</label>
              <input id="months" name="months" [(ngModel)]="form.intervalMonths" type="number" min="1" max="60">
            </div>
          </div>

          <div class="inline" style="justify-content:flex-end;margin-top:8px">
            <button class="btn btn-secondary btn-sm" (click)="close()">Cancelar</button>
            <button class="btn btn-primary btn-sm" [disabled]="saving()" (click)="save()">
              @if (saving()) { <span class="spinner spinner-sm"></span> }
              Salvar
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .row.off { opacity: .6; }
    .code { font-size: 12px; font-weight: 500; margin-left: 6px; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  `],
})
export class AdminPlansComponent implements OnInit {
  private api = inject(PlansApiService);
  private toast = inject(ToastService);

  plans = signal<Plan[]>([]);
  loading = signal(true);
  saving = signal(false);
  busy = signal<number | null>(null);
  editing = signal(false);
  isNew = signal(true);

  private editingId: number | null = null;

  form = { code: '', name: '', description: '', priceReais: 0, intervalMonths: 1 };

  ngOnInit() { this.load(); }

  money(reais: number) {
    return reais.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  openNew() {
    this.isNew.set(true);
    this.editingId = null;
    this.form = { code: '', name: '', description: '', priceReais: 0, intervalMonths: 1 };
    this.editing.set(true);
  }

  openEdit(p: Plan) {
    this.isNew.set(false);
    this.editingId = p.id;
    this.form = {
      code: p.code,
      name: p.name,
      description: p.description ?? '',
      priceReais: p.priceReais,
      intervalMonths: p.intervalMonths,
    };
    this.editing.set(true);
  }

  close() { this.editing.set(false); }

  save() {
    const f = this.form;
    if (!f.name.trim() || (this.isNew() && !f.code.trim())) {
      this.toast.error('Preencha código e nome.');
      return;
    }
    // Arredonda na conversão: preço em centavos não aceita fração.
    const priceCents = Math.round(Number(f.priceReais) * 100);
    if (!Number.isFinite(priceCents) || priceCents < 0) {
      this.toast.error('Preço inválido.');
      return;
    }

    this.saving.set(true);
    const done = () => {
      this.saving.set(false);
      this.editing.set(false);
      this.toast.success('Plano salvo.');
      this.load();
    };
    const fail = (e: unknown) => {
      this.saving.set(false);
      this.toast.fromApi(e, 'Não foi possível salvar o plano.');
    };

    if (this.isNew()) {
      this.api.create({
        code: f.code.trim().toUpperCase(),
        name: f.name.trim(),
        description: f.description.trim() || undefined,
        priceCents,
        intervalMonths: Number(f.intervalMonths),
      }).subscribe({ next: done, error: fail });
    } else {
      this.api.update(this.editingId!, {
        name: f.name.trim(),
        description: f.description.trim(),
        priceCents,
        intervalMonths: Number(f.intervalMonths),
      }).subscribe({ next: done, error: fail });
    }
  }

  deactivate(p: Plan) {
    this.busy.set(p.id);
    this.api.deactivate(p.id).subscribe({
      next: () => {
        this.busy.set(null);
        this.toast.info(`${p.name} desativado. Assinaturas atuais continuam valendo.`);
        this.load();
      },
      error: e => {
        this.busy.set(null);
        this.toast.fromApi(e, 'Não foi possível desativar o plano.');
      },
    });
  }

  private load() {
    this.loading.set(true);
    this.api.listAll().subscribe({
      next: p => { this.plans.set(p); this.loading.set(false); },
      error: e => {
        this.loading.set(false);
        this.toast.fromApi(e, 'Não foi possível carregar os planos.');
      },
    });
  }
}

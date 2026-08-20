import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CheckinApiService } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';
import { CheckinItem, CheckinProvider, StartCheckinResponse } from '../../core/models';
import { IconComponent } from '../../shared/icon.component';

interface ProviderOption {
  value: CheckinProvider;
  label: string;
  hint: string;
}

@Component({
  selector: 'app-checkin',
  standalone: true,
  imports: [DatePipe, FormsModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="page-title">
        <h1>Check-in</h1>
        <span class="page-subtitle">Registre sua entrada na academia.</span>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat stat-accent enter">
        <span class="stat-label">Check-ins no mês</span>
        <span class="stat-value nums">{{ monthCount() }}</span>
        <span class="stat-hint">{{ lastLabel() }}</span>
      </div>
      <div class="stat enter">
        <span class="stat-label">Total registrado</span>
        <span class="stat-value nums">{{ history().length }}</span>
        <span class="stat-hint">desde o primeiro acesso</span>
      </div>
    </div>

    <div class="section-title">Fazer check-in</div>

    <div class="picker">
      @for (p of providers; track p.value) {
        <button class="prov" [class.active]="provider().value === p.value" (click)="select(p)">
          <span class="prov-name">{{ p.label }}</span>
          <span class="prov-hint">{{ p.hint }}</span>
        </button>
      }
    </div>

    <div class="card start-card">
      @if (provider().value === 'WELLHUB') {
        <div class="alert alert-info">
          <app-icon name="check" [size]="17" />
          <span>Faça o check-in no aplicativo do Wellhub primeiro. Aqui a academia
                só confirma que ele existe e está no prazo.</span>
        </div>

        <div class="form-group">
          <label for="wellhubId">Wellhub ID <span class="dim">(só na primeira visita)</span></label>
          <input id="wellhubId" name="wellhubId" [(ngModel)]="wellhubId"
                 placeholder="13 dígitos, como aparece no app" inputmode="numeric"
                 (keyup.enter)="start()">
          <span class="field-hint">Depois de cadastrado, a recepção não precisa digitar nada.</span>
        </div>
      } @else {
        <div class="form-group">
          <label for="gym">Unidade (opcional)</label>
          <input id="gym" name="gym" [(ngModel)]="gymName" placeholder="Ex.: Unidade Centro"
                 (keyup.enter)="start()">
        </div>
      }

      <button class="btn btn-primary btn-full" [disabled]="sending()" (click)="start()">
        @if (sending()) { <span class="spinner spinner-sm"></span> }
        Confirmar entrada
      </button>

      @if (result(); as r) {
        <div class="result" [class.ok]="r.approved" [class.bad]="!r.approved">
          <app-icon [name]="r.approved ? 'check' : 'x'" [size]="20" />
          <div>
            <strong>{{ r.approved ? 'Entrada liberada' : 'Entrada recusada' }}</strong>
            <span>{{ r.approved ? (r.memberName ?? 'Check-in registrado.') : (r.message ?? 'O parceiro não autorizou.') }}</span>
          </div>
        </div>
      }
    </div>

    <div class="card note">
      <strong>TotalPass</strong>
      <span class="muted">A entrada do TotalPass chega sozinha: você faz o check-in no
        aplicativo deles e a recepção libera pela fila de check-ins recebidos.</span>
    </div>

    <div class="section-title">Histórico</div>

    @if (loading()) {
      <div class="rows">
        @for (i of [1,2,3]; track i) { <div class="skeleton" style="height:58px"></div> }
      </div>
    } @else if (history().length === 0) {
      <div class="empty-state">
        <div class="icon"><app-icon name="check" [size]="40" [stroke]="1.4" /></div>
        <h3>Nenhum check-in ainda</h3>
        <p>Assim que você registrar a primeira entrada, ela aparece aqui.</p>
      </div>
    } @else {
      <div class="rows">
        @for (h of history(); track h.id) {
          <div class="row enter">
            <div class="row-main">
              <span class="row-title">{{ providerLabel(h.provider) }}</span>
              <span class="row-sub nums">
                {{ h.startedAt | date:'dd/MM/yyyy · HH:mm' }}
                @if (h.partnerPlan) { <span class="dim">· {{ h.partnerPlan }}</span> }
              </span>
              @if (h.failureReason) { <span class="reason">{{ h.failureReason }}</span> }
            </div>
            <span class="badge" [class]="badgeClass(h.status)">{{ statusLabel(h.status) }}</span>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .picker { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 10px; margin-bottom: 14px; }
    .prov { display: flex; flex-direction: column; gap: 4px; padding: 14px 16px; text-align: left; background: var(--surface); border: 1px solid var(--border); border-radius: var(--r); color: var(--text); font-family: var(--font); cursor: pointer; transition: border-color var(--t-fast), background var(--t-fast); }
    .prov:hover { border-color: var(--border-strong); background: var(--surface-hi); }
    .prov.active { border-color: var(--accent); background: var(--accent-soft); }
    .prov-name { font-size: 14px; font-weight: 700; }
    .prov-hint { font-size: 12px; color: var(--text-muted); }
    .start-card { max-width: 520px; }
    .note { max-width: 520px; margin-top: 12px; display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
    .result { display: flex; align-items: flex-start; gap: 10px; margin-top: 16px; padding: 12px 14px; border-radius: var(--r); animation: rise var(--t-slow) both; }
    .result div { display: flex; flex-direction: column; gap: 2px; font-size: 13px; }
    .result strong { font-size: 14px; }
    .result.ok  { background: var(--success-soft); color: var(--success); }
    .result.bad { background: var(--danger-soft);  color: var(--danger); }
    .reason { font-size: 12px; color: var(--danger); }
  `],
})
export class CheckinComponent implements OnInit {
  private api = inject(CheckinApiService);
  private toast = inject(ToastService);

  /**
   * TotalPass não aparece aqui: naquele fluxo quem inicia é o parceiro, que
   * avisa a academia por webhook. Oferecer o botão só levaria a um erro.
   */
  readonly providers: ProviderOption[] = [
    { value: 'WELLHUB', label: 'Wellhub', hint: 'antigo Gympass' },
    { value: 'DIRECT',  label: 'Aluno',   hint: 'plano da casa' },
  ];

  provider = signal<ProviderOption>(this.providers[0]);
  wellhubId = '';
  gymName = '';

  history = signal<CheckinItem[]>([]);
  loading = signal(true);
  sending = signal(false);
  result = signal<StartCheckinResponse | null>(null);

  monthCount = computed(() => {
    const now = new Date();
    return this.history().filter(h => {
      const d = new Date(h.startedAt);
      return h.status === 'COMPLETED'
        && d.getMonth() === now.getMonth()
        && d.getFullYear() === now.getFullYear();
    }).length;
  });

  lastLabel = computed(() => {
    const last = this.history()[0];
    if (!last) return 'nenhum registro';
    return 'último em ' + new Date(last.startedAt).toLocaleDateString('pt-BR');
  });

  ngOnInit() { this.load(); }

  select(p: ProviderOption) {
    this.provider.set(p);
    this.result.set(null);
  }

  providerLabel(p: string) {
    return p === 'WELLHUB' ? 'Wellhub' : p === 'TOTALPASS' ? 'TotalPass' : 'Aluno';
  }

  statusLabel(s: string) {
    return s === 'COMPLETED' ? 'Liberado' : s === 'FAILED' ? 'Recusado' : 'Pendente';
  }

  badgeClass(s: string) {
    return s === 'COMPLETED' ? 'badge-success' : s === 'FAILED' ? 'badge-danger' : 'badge-warning';
  }

  start() {
    const p = this.provider();
    this.sending.set(true);
    this.result.set(null);

    this.api.start({
      provider: p.value,
      code: p.value === 'WELLHUB' ? (this.wellhubId.trim() || undefined) : undefined,
      gymName: p.value === 'DIRECT' ? (this.gymName.trim() || undefined) : undefined,
    }).subscribe({
      next: r => {
        this.sending.set(false);
        this.result.set(r);
        // Recusa do parceiro é resposta 200 com approved=false, não erro HTTP.
        if (r.approved) {
          this.toast.success('Entrada liberada.');
          this.wellhubId = '';
        } else {
          this.toast.error(r.message ?? 'Entrada recusada pelo parceiro.');
        }
        this.load();
      },
      error: e => {
        this.sending.set(false);
        this.toast.fromApi(e, 'Não foi possível registrar o check-in.');
      },
    });
  }

  private load() {
    this.loading.set(true);
    this.api.myHistory().subscribe({
      next: h => { this.history.set(h); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}

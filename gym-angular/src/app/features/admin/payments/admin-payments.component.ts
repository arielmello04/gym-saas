import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { PaymentApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { AdminSubscriptionItem } from '../../../core/models';
import { IconComponent } from '../../../shared/icon.component';

@Component({
  selector: 'app-admin-payments',
  standalone: true,
  imports: [DatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="page-title">
        <h1>Assinaturas</h1>
        <span class="page-subtitle">Situação de pagamento dos alunos.</span>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat stat-accent enter">
        <span class="stat-label">Receita contratada</span>
        <span class="stat-value nums">R$ {{ receitaAtiva() }}</span>
        <span class="stat-hint">soma das assinaturas ativas, por ciclo</span>
      </div>
      <div class="stat enter">
        <span class="stat-label">Ativas</span>
        <span class="stat-value nums">{{ count('ACTIVE') }}</span>
        <span class="stat-hint">em dia</span>
      </div>
      <div class="stat enter">
        <span class="stat-label">Em atraso</span>
        <span class="stat-value nums" [style.color]="count('PAST_DUE') ? 'var(--warning)' : null">
          {{ count('PAST_DUE') }}
        </span>
        <span class="stat-hint">exigem cobrança</span>
      </div>
      <div class="stat enter">
        <span class="stat-label">Canceladas</span>
        <span class="stat-value nums">{{ count('CANCELED') }}</span>
        <span class="stat-hint">no histórico</span>
      </div>
    </div>

    <div class="section-title">Todas as assinaturas</div>

    @if (loading()) {
      <div class="rows">
        @for (i of [1,2,3]; track i) { <div class="skeleton" style="height:64px"></div> }
      </div>
    } @else if (subs().length === 0) {
      <div class="empty-state">
        <div class="icon"><app-icon name="money" [size]="40" [stroke]="1.4" /></div>
        <h3>Nenhuma assinatura ainda</h3>
        <p>Assim que um aluno assinar um plano, ele aparece aqui.</p>
      </div>
    } @else {
      <div class="rows">
        @for (s of subs(); track s.id) {
          <div class="row enter">
            <div class="row-main">
              <span class="row-title truncate">{{ s.userEmail }}</span>
              <span class="row-sub nums">
                {{ s.planName }} · R$ {{ money(s.priceReais) }}
                @if (s.status !== 'CANCELED') { · próxima em {{ s.nextBillingAt | date:'dd/MM/yyyy' }} }
                @else if (s.canceledAt) { · cancelada em {{ s.canceledAt | date:'dd/MM/yyyy' }} }
              </span>
            </div>
            <span class="badge" [class]="statusClass(s.status)">{{ statusLabel(s.status) }}</span>
          </div>
        }
      </div>
    }
  `,
  styles: [``],
})
export class AdminPaymentsComponent implements OnInit {
  private api = inject(PaymentApiService);
  private toast = inject(ToastService);

  subs = signal<AdminSubscriptionItem[]>([]);
  loading = signal(true);

  /**
   * Soma o valor das assinaturas ativas.
   *
   * Não é MRR: um plano anual entra pelo valor cheio do ciclo, não dividido por
   * doze. Para receita mensal de verdade seria preciso o ciclo de cada plano,
   * que esta listagem ainda não carrega.
   */
  receitaAtiva = computed(() => {
    const total = this.subs()
      .filter(s => s.status === 'ACTIVE')
      .reduce((sum, s) => sum + s.priceReais, 0);
    return this.money(total);
  });

  ngOnInit() {
    this.api.adminSubscriptions().subscribe({
      next: s => { this.subs.set(s); this.loading.set(false); },
      error: e => {
        this.loading.set(false);
        this.toast.fromApi(e, 'Não foi possível carregar as assinaturas.');
      },
    });
  }

  count(status: string) {
    return this.subs().filter(s => s.status === status).length;
  }

  money(reais: number) {
    return reais.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  statusLabel(s: string) {
    return s === 'ACTIVE' ? 'Ativa' : s === 'PAST_DUE' ? 'Em atraso' : s === 'CANCELED' ? 'Cancelada' : s;
  }

  statusClass(s: string) {
    return s === 'ACTIVE' ? 'badge-success' : s === 'PAST_DUE' ? 'badge-warning' : 'badge-neutral';
  }
}

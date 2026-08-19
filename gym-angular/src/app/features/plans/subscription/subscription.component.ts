import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { PaymentApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { PaymentItem, Subscription } from '../../../core/models';
import { IconComponent } from '../../../shared/icon.component';

@Component({
  selector: 'app-subscription',
  standalone: true,
  imports: [DatePipe, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="page-title">
        <h1>Assinatura</h1>
        <span class="page-subtitle">Seu plano e o histórico de cobranças.</span>
      </div>
    </div>

    @if (loading()) {
      <div class="skeleton" style="height:180px;margin-bottom:16px"></div>
      <div class="skeleton" style="height:120px"></div>
    } @else if (!sub()) {
      <div class="empty-state">
        <div class="icon"><app-icon name="card" [size]="40" [stroke]="1.4" /></div>
        <h3>Sem assinatura ativa</h3>
        <p>Escolha um plano para liberar as reservas de aula.</p>
        <a routerLink="/plans" class="btn btn-primary">Ver planos</a>
      </div>
    } @else {
      <div class="hero enter">
        <div class="hero-top spread">
          <div>
            <span class="hero-label">Plano atual</span>
            <h2 class="hero-plan">{{ sub()!.planName }}</h2>
          </div>
          <span class="badge" [class]="statusClass(sub()!.status)">{{ statusLabel(sub()!.status) }}</span>
        </div>

        <div class="hero-price">
          <span class="hero-currency">R$</span>
          <span class="hero-value nums">{{ money(sub()!.priceCents) }}</span>
        </div>

        <div class="hero-facts">
          <div>
            <span class="fact-label">Período atual</span>
            <span class="nums">{{ sub()!.currentPeriodStart | date:'dd/MM/yy' }} — {{ sub()!.currentPeriodEnd | date:'dd/MM/yy' }}</span>
          </div>
          <div>
            <span class="fact-label">Próxima cobrança</span>
            <span class="nums">{{ sub()!.nextBillingAt | date:'dd/MM/yyyy' }}</span>
          </div>
          <div>
            <span class="fact-label">Dia de vencimento</span>
            <span class="nums">{{ sub()!.billingDay }}</span>
          </div>
        </div>

        @if (sub()!.status === 'ACTIVE' || sub()!.status === 'PAST_DUE') {
          <button class="btn btn-danger btn-sm" [disabled]="canceling()" (click)="askCancel()">
            @if (canceling()) { <span class="spinner spinner-sm"></span> }
            Cancelar assinatura
          </button>
        }
      </div>

      <div class="section-title">Cobranças</div>

      @if (invoices().length === 0) {
        <p class="muted">Nenhuma cobrança registrada ainda.</p>
      } @else {
        <div class="rows">
          @for (inv of invoices(); track inv.id) {
            <div class="row enter">
              <div class="row-main">
                <span class="row-title nums">R$ {{ money(inv.amountCents) }}</span>
                <span class="row-sub nums">
                  Vencimento {{ inv.dueAt | date:'dd/MM/yyyy' }}
                  @if (inv.paidAt) { · pago em {{ inv.paidAt | date:'dd/MM/yyyy' }} }
                </span>
              </div>
              <span class="badge" [class]="invoiceClass(inv.status)">{{ invoiceLabel(inv.status) }}</span>
            </div>
          }
        </div>
      }
    }

    @if (confirming()) {
      <div class="modal-overlay" (click)="confirming.set(false)">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>Cancelar assinatura?</h3>
          <p class="muted">Você mantém o acesso até o fim do período já pago, em
            {{ sub()!.currentPeriodEnd | date:'dd/MM/yyyy' }}. Reservas futuras podem ser canceladas.</p>
          <div class="inline" style="margin-top:20px;justify-content:flex-end">
            <button class="btn btn-secondary btn-sm" (click)="confirming.set(false)">Manter</button>
            <button class="btn btn-danger btn-sm" (click)="cancel()">Cancelar assinatura</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .hero { padding: 26px; background: var(--surface); border: 1px solid var(--accent-line); border-radius: var(--r-lg); box-shadow: var(--glow); }
    .hero-top { align-items: flex-start; margin-bottom: 14px; }
    .hero-label { display: block; font-size: 12px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; color: var(--text-dim); margin-bottom: 4px; }
    .hero-plan { font-size: 22px; }
    .hero-price { display: flex; align-items: baseline; gap: 5px; margin-bottom: 22px; }
    .hero-currency { font-size: 17px; font-weight: 600; color: var(--text-muted); }
    .hero-value { font-size: 44px; font-weight: 800; letter-spacing: -.045em; line-height: 1; color: var(--accent); }
    .hero-facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 16px; padding: 16px 0; margin-bottom: 18px; border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); font-size: 14px; }
    .hero-facts > div { display: flex; flex-direction: column; gap: 3px; }
    .fact-label { font-size: 12px; color: var(--text-dim); }
  `],
})
export class SubscriptionComponent implements OnInit {
  private api = inject(PaymentApiService);
  private toast = inject(ToastService);

  sub = signal<Subscription | null>(null);
  invoices = signal<PaymentItem[]>([]);
  loading = signal(true);
  canceling = signal(false);
  confirming = signal(false);

  ngOnInit() { this.load(); }

  money(cents: number) {
    return (cents / 100).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  statusLabel(s: string) {
    return s === 'ACTIVE' ? 'Ativa' : s === 'PAST_DUE' ? 'Em atraso' : s === 'CANCELED' ? 'Cancelada' : s;
  }
  statusClass(s: string) {
    return s === 'ACTIVE' ? 'badge-success' : s === 'PAST_DUE' ? 'badge-warning' : 'badge-neutral';
  }
  invoiceLabel(s: string) {
    return s === 'PAID' ? 'Pago' : s === 'PENDING' ? 'Pendente' : s === 'FAILED' ? 'Falhou' : s;
  }
  invoiceClass(s: string) {
    return s === 'PAID' ? 'badge-success' : s === 'PENDING' ? 'badge-warning' : s === 'FAILED' ? 'badge-danger' : 'badge-neutral';
  }

  askCancel() { this.confirming.set(true); }

  cancel() {
    this.confirming.set(false);
    this.canceling.set(true);
    this.api.cancel().subscribe({
      next: () => {
        this.canceling.set(false);
        this.toast.success('Assinatura cancelada.');
        this.load();
      },
      error: e => {
        this.canceling.set(false);
        this.toast.fromApi(e, 'Não foi possível cancelar a assinatura.');
      },
    });
  }

  private load() {
    this.loading.set(true);
    this.api.mySubscription().subscribe({
      next: s => {
        this.sub.set(s);
        this.loading.set(false);
        this.api.myInvoices().subscribe({ next: i => this.invoices.set(i), error: () => {} });
      },
      // 404/409 aqui significa "ainda não assinou", que é estado normal.
      error: () => { this.sub.set(null); this.invoices.set([]); this.loading.set(false); },
    });
  }
}

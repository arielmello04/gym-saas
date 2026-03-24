import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';

interface Subscription { id: number; planName: string; priceCents: number; currency: string; billingDay: number; status: string; currentPeriodStart: string; currentPeriodEnd: string; nextBillingAt: string; }
interface PaymentItem  { id: number; amountCents: number; currency: string; status: string; providerRef?: string; dueAt: string; paidAt?: string; createdAt: string; }

@Component({
  selector: 'app-subscription',
  standalone: true,
  imports: [DatePipe, RouterLink],
  template: `
    <div class="page-header"><h1>📄 Minha Assinatura</h1></div>

    @if (loading()) {
      <div style="display:flex;justify-content:center;padding:48px"><div class="spinner"></div></div>
    } @else if (!sub()) {
      <div class="empty-state">
        <div class="icon">💳</div><h3>Sem assinatura ativa</h3>
        <p>Escolha um plano para começar.</p>
        <a routerLink="/plans" class="btn btn-primary" style="margin-top:16px">Ver planos</a>
      </div>
    } @else {
      <div class="sub-card card">
        <div class="sub-header">
          <div>
            <h2>{{ sub()!.planName }}</h2>
            <p class="sub-price">R$ {{ (sub()!.priceCents / 100).toFixed(2) }}/mês</p>
          </div>
          <span class="badge" [class]="statusBadge(sub()!.status)">{{ sub()!.status }}</span>
        </div>
        <div class="sub-dates">
          <div><span class="label">Período atual</span><span>{{ sub()!.currentPeriodStart | date:'dd/MM/yyyy' }} – {{ sub()!.currentPeriodEnd | date:'dd/MM/yyyy' }}</span></div>
          <div><span class="label">Próxima cobrança</span><span>{{ sub()!.nextBillingAt | date:'dd/MM/yyyy' }}</span></div>
        </div>
        @if (sub()!.status === 'ACTIVE') {
          <button class="btn btn-danger btn-sm" [disabled]="canceling()" (click)="cancel()">
            @if (canceling()) { <span class="spinner" style="width:14px;height:14px"></span> }
            Cancelar assinatura
          </button>
        }
      </div>

      <h2 style="margin:28px 0 16px;font-size:18px">Histórico de cobranças</h2>
      @if (invoices().length === 0) {
        <p style="color:var(--color-text-muted)">Nenhuma cobrança encontrada.</p>
      } @else {
        <div class="invoices-list">
          @for (inv of invoices(); track inv.id) {
            <div class="invoice-row card">
              <div>
                <span class="inv-amount">R$ {{ (inv.amountCents / 100).toFixed(2) }}</span>
                <span class="inv-date">Venc: {{ inv.dueAt | date:'dd/MM/yyyy' }}</span>
              </div>
              <div style="display:flex;align-items:center;gap:10px">
                @if (inv.paidAt) { <span class="inv-paid">Pago em {{ inv.paidAt | date:'dd/MM/yyyy' }}</span> }
                <span class="badge" [class]="invBadge(inv.status)">{{ inv.status }}</span>
              </div>
            </div>
          }
        </div>
      }
    }
  `,
  styles: [`
    .sub-card { .sub-header{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:16px; h2{margin:0 0 4px;} .sub-price{margin:0;color:var(--color-text-muted);font-size:14px;}}
      .sub-dates { display:flex; gap:24px; flex-wrap:wrap; margin-bottom:20px;
        > div { display:flex; flex-direction:column; gap:2px; .label{font-size:12px;color:var(--color-text-muted);font-weight:500;} font-size:14px; }
      }
    }
    .invoices-list { display:flex; flex-direction:column; gap:8px; }
    .invoice-row { padding:12px 16px; display:flex; justify-content:space-between; align-items:center;
      .inv-amount{font-weight:600;margin-right:12px;} .inv-date{font-size:13px;color:var(--color-text-muted);} .inv-paid{font-size:12px;color:var(--color-success);}
    }
  `],
})
export class SubscriptionComponent implements OnInit {
  sub       = signal<Subscription | null>(null);
  invoices  = signal<PaymentItem[]>([]);
  loading   = signal(true);
  canceling = signal(false);

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.http.get<Subscription>('/api/v1/payments/my-subscription').subscribe({
      next: (s) => {
        this.sub.set(s); this.loading.set(false);
        this.http.get<PaymentItem[]>('/api/v1/payments/invoices').subscribe({ next: (i) => this.invoices.set(i) });
      },
      error: () => { this.sub.set(null); this.loading.set(false); },
    });
  }

  cancel() {
    if (!confirm('Cancelar assinatura? Você perde acesso ao fim do período atual.')) return;
    this.canceling.set(true);
    this.http.delete('/api/v1/payments/cancel').subscribe({
      next:  () => { this.canceling.set(false); this.ngOnInit(); },
      error: () => this.canceling.set(false),
    });
  }

  statusBadge(s: string) { return s === 'ACTIVE' ? 'badge badge-success' : s === 'PAST_DUE' ? 'badge badge-warning' : 'badge badge-neutral'; }
  invBadge(s: string)    { return s === 'PAID' ? 'badge badge-success' : s === 'PENDING' ? 'badge badge-warning' : s === 'FAILED' ? 'badge badge-danger' : 'badge badge-neutral'; }
}

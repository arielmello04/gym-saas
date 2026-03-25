import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { PaymentApiService } from '../../../core/services/api.service';
import { AdminSubscriptionSummary } from '../../../core/models';

@Component({
  selector: 'app-admin-payments',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="page-header"><h1>💰 Pagamentos</h1></div>

    @if (loading()) {
      <div style="display:flex;justify-content:center;padding:48px"><div class="spinner"></div></div>
    } @else if (subs().length === 0) {
      <div class="empty-state">
        <div class="icon">💳</div><h3>Nenhuma assinatura ativa</h3>
      </div>
    } @else {
      <div class="subs-list">
        @for (s of subs(); track s.id) {
          <div class="sub-row card">
            <div class="sub-info">
              <span class="email">{{ s.userEmail }}</span>
              <span class="plan">{{ s.planName }} — R$ {{ (s.priceCents / 100).toFixed(2) }}</span>
              <span class="dates">Próxima cobrança: {{ s.nextBillingAt | date:'dd/MM/yyyy' }}</span>
            </div>
            <span class="badge" [class]="statusBadge(s.status)">{{ s.status }}</span>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .subs-list { display:flex; flex-direction:column; gap:10px; }
    .sub-row { padding:14px 18px; display:flex; justify-content:space-between; align-items:center;
      .sub-info { display:flex; flex-direction:column; gap:3px;
        .email{font-weight:600;font-size:14px;} .plan{font-size:13px;color:var(--color-text-muted);} .dates{font-size:12px;color:var(--color-text-muted);}
      }
    }
  `],
})
export class AdminPaymentsComponent implements OnInit {
  subs    = signal<AdminSubscriptionSummary[]>([]);
  loading = signal(true);

  constructor(private api: PaymentApiService) {}

  ngOnInit() {
    this.api.adminSubscriptions().subscribe({
      next:  (s) => { this.subs.set(s); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  statusBadge(s: string) { return s === 'ACTIVE' ? 'badge badge-success' : s === 'PAST_DUE' ? 'badge badge-warning' : 'badge badge-neutral'; }
}

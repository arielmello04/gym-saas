import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Router } from '@angular/router';
import { PaymentApiService, PlansApiService } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';
import { Plan } from '../../core/models';
import { IconComponent } from '../../shared/icon.component';

type Method = 'pix' | 'boleto';

@Component({
  selector: 'app-plans',
  standalone: true,
  imports: [RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="page-title">
        <h1>Planos</h1>
        <span class="page-subtitle">Escolha o plano e a forma de pagamento.</span>
      </div>
      <a routerLink="/subscription" class="btn btn-secondary btn-sm">Minha assinatura</a>
    </div>

    <div class="methods">
      <span class="methods-label">Pagamento</span>
      @for (m of methods; track m.value) {
        <button class="method" [class.active]="method() === m.value" (click)="method.set(m.value)">
          {{ m.label }}
        </button>
      }
    </div>

    @if (loading()) {
      <div class="grid-cards">
        @for (i of [1,2,3]; track i) { <div class="skeleton" style="height:230px"></div> }
      </div>
    } @else if (plans().length === 0) {
      <div class="empty-state">
        <div class="icon"><app-icon name="tag" [size]="40" [stroke]="1.4" /></div>
        <h3>Nenhum plano disponível</h3>
        <p>Esta academia ainda não publicou planos. Fale com a recepção.</p>
      </div>
    } @else {
      <div class="grid-cards">
        @for (p of plans(); track p.id; let i = $index) {
          <article class="plan enter" [class.featured]="i === 1">
            @if (i === 1) { <span class="tagline">Mais escolhido</span> }

            <h2 class="p-name">{{ p.name }}</h2>
            @if (p.description) { <p class="p-desc">{{ p.description }}</p> }

            <div class="p-price">
              <span class="p-currency">R$</span>
              <span class="p-value nums">{{ formatValue(p.priceReais) }}</span>
              <span class="p-period">/ {{ p.intervalLabel }}</span>
            </div>
            <p class="p-month nums">{{ perMonth(p) }}</p>

            <button class="btn btn-full" [class.btn-primary]="i === 1" [class.btn-secondary]="i !== 1"
                    [disabled]="subscribing() !== null" (click)="subscribe(p)">
              @if (subscribing() === p.id) { <span class="spinner spinner-sm"></span> }
              Assinar
            </button>
          </article>
        }
      </div>
    }
  `,
  styles: [`
    .methods { display: flex; align-items: center; gap: 8px; margin-bottom: 22px; flex-wrap: wrap; }
    .methods-label { font-size: 12px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; color: var(--text-dim); margin-right: 4px; }
    .method { padding: 7px 16px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--r-pill); color: var(--text-muted); font-family: var(--font); font-size: 13px; font-weight: 600; cursor: pointer; transition: all var(--t-fast); }
    .method:hover { border-color: var(--border-strong); color: var(--text); }
    .method.active { background: var(--accent); border-color: var(--accent); color: var(--accent-ink); }

    .plan { position: relative; display: flex; flex-direction: column; gap: 8px; padding: 24px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--r-lg); transition: border-color var(--t), transform var(--t); }
    .plan:hover { border-color: var(--border-strong); transform: translateY(-3px); }
    .plan.featured { border-color: var(--accent-line); box-shadow: var(--glow); }
    .tagline { position: absolute; top: -10px; left: 24px; padding: 3px 10px; background: var(--accent); color: var(--accent-ink); border-radius: var(--r-pill); font-size: 11px; font-weight: 800; letter-spacing: .04em; text-transform: uppercase; }
    .p-name { font-size: 17px; }
    .p-desc { font-size: 13px; color: var(--text-muted); min-height: 36px; }
    .p-price { display: flex; align-items: baseline; gap: 4px; margin-top: 6px; }
    .p-currency { font-size: 15px; font-weight: 600; color: var(--text-muted); }
    .p-value { font-size: 38px; font-weight: 800; letter-spacing: -.045em; line-height: 1; }
    .p-period { font-size: 13px; color: var(--text-muted); }
    .p-month { font-size: 12px; color: var(--text-dim); margin-bottom: 10px; }
    .featured .p-value { color: var(--accent); }
  `],
})
export class PlansComponent implements OnInit {
  private plansApi = inject(PlansApiService);
  private paymentApi = inject(PaymentApiService);
  private toast = inject(ToastService);
  private router = inject(Router);

  readonly methods: { value: Method; label: string }[] = [
    { value: 'pix',    label: 'Pix' },
    { value: 'boleto', label: 'Boleto' },
  ];

  plans = signal<Plan[]>([]);
  loading = signal(true);
  subscribing = signal<number | null>(null);
  method = signal<Method>('pix');

  ngOnInit() {
    this.plansApi.list().subscribe({
      next: p => { this.plans.set(p); this.loading.set(false); },
      error: e => {
        this.loading.set(false);
        this.toast.fromApi(e, 'Não foi possível carregar os planos.');
      },
    });
  }

  formatValue(reais: number) {
    return reais.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  /** Comparação honesta entre planos de ciclos diferentes. */
  perMonth(p: Plan) {
    if (p.intervalMonths <= 1) return 'cobrado todo mês';
    const mensal = p.priceReais / p.intervalMonths;
    return `equivale a R$ ${this.formatValue(mensal)} por mês`;
  }

  subscribe(plan: Plan) {
    this.subscribing.set(plan.id);
    // Só o id vai no corpo: o preço é do catálogo do servidor.
    this.paymentApi.subscribe({ planId: plan.id, paymentMethod: this.method() }).subscribe({
      next: () => {
        this.subscribing.set(null);
        this.toast.success(`Assinatura do plano ${plan.name} criada.`);
        this.router.navigate(['/subscription']);
      },
      error: e => {
        this.subscribing.set(null);
        this.toast.fromApi(e, 'Não foi possível concluir a assinatura.');
      },
    });
  }
}

import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';

interface Plan { id: number; name: string; description: string; priceCents: number; priceReais: number; currency: string; intervalMonths: number; intervalLabel: string; }
interface SubscribeResponse { subscriptionId: number; paymentStatus: string; providerRef: string; pixCode?: string; pixQrCode?: string; boletoBarcode?: string; boletoUrl?: string; expiresAt?: string; }

@Component({
  selector: 'app-plans',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="page-header">
      <h1>💳 Planos</h1>
      <a routerLink="/subscription" class="btn btn-secondary btn-sm">Minha assinatura →</a>
    </div>

    @if (loading()) {
      <div style="display:flex;justify-content:center;padding:48px"><div class="spinner"></div></div>
    } @else {
      <div class="plans-grid">
        @for (p of plans(); track p.id) {
          <div class="plan-card card">
            <h3 class="plan-name">{{ p.name }}</h3>
            <p class="plan-desc">{{ p.description }}</p>
            <div class="plan-price">
              <span class="price-value">R$ {{ p.priceReais.toFixed(2) }}</span>
              <span class="price-period">/ {{ p.intervalLabel }}</span>
            </div>

            <div class="method-selector">
              <label>Pagamento</label>
              <div class="method-btns">
                @for (m of methods; track m.value) {
                  <button class="method-btn" [class.active]="selectedMethod() === m.value"
                          (click)="selectedMethod.set(m.value)">
                    {{ m.icon }} {{ m.label }}
                  </button>
                }
              </div>
            </div>

            <button class="btn btn-primary btn-full" [disabled]="subscribing() === p.id" (click)="subscribe(p)">
              @if (subscribing() === p.id) { <span class="spinner" style="width:15px;height:15px"></span> }
              Assinar
            </button>
          </div>
        }
      </div>

      @if (pixResult()) {
        <div class="modal-overlay" (click)="pixResult.set(null)">
          <div class="modal-card card" (click)="$event.stopPropagation()">
            <h3>✅ Pague via Pix</h3>
            @if (pixResult()!.pixQrCode) {
              <img [src]="'data:image/png;base64,' + pixResult()!.pixQrCode" alt="QR Code Pix" class="pix-qr">
            }
            @if (pixResult()!.pixCode) {
              <p class="pix-label">Copia e cola:</p>
              <textarea readonly [value]="pixResult()!.pixCode!" rows="3" style="width:100%;resize:none;font-size:12px;font-family:monospace;padding:8px;border-radius:6px;border:1px solid var(--color-border)"></textarea>
              <button class="btn btn-secondary btn-sm" style="margin-top:8px" (click)="copyPix()">
                {{ copied() ? '✅ Copiado!' : '📋 Copiar' }}
              </button>
            }
            <p style="font-size:13px;color:var(--color-text-muted);margin:12px 0">Pix expira em 24h. Assinatura ativada automaticamente após pagamento.</p>
            <button class="btn btn-primary" (click)="pixResult.set(null)">Fechar</button>
          </div>
        </div>
      }

      @if (boletoResult()) {
        <div class="modal-overlay" (click)="boletoResult.set(null)">
          <div class="modal-card card" (click)="$event.stopPropagation()">
            <h3>📄 Boleto gerado</h3>
            @if (boletoResult()!.boletoUrl) {
              <a [href]="boletoResult()!.boletoUrl" target="_blank" class="btn btn-primary btn-full">Abrir boleto</a>
            }
            <p style="font-size:13px;color:var(--color-text-muted);margin:12px 0">Vencimento em 3 dias úteis.</p>
            <button class="btn btn-secondary" (click)="boletoResult.set(null)">Fechar</button>
          </div>
        </div>
      }
    }
  `,
  styles: [`
    .plans-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); gap:20px; }
    .plan-card { display:flex; flex-direction:column; gap:12px;
      .plan-name { margin:0; font-size:18px; font-weight:700; }
      .plan-desc { margin:0; font-size:14px; color:var(--color-text-muted); }
      .plan-price { .price-value{font-size:28px;font-weight:700;color:var(--color-primary);} .price-period{font-size:14px;color:var(--color-text-muted);} }
    }
    .method-selector { label{font-size:12px;font-weight:500;color:var(--color-text-muted);margin-bottom:6px;display:block;}
      .method-btns { display:flex; gap:6px; }
      .method-btn { padding:6px 12px; border:1.5px solid var(--color-border); border-radius:20px; background:none; cursor:pointer; font-size:13px; transition:all var(--transition);
        &.active { border-color:var(--color-primary); background:#eef2ff; color:var(--color-primary); font-weight:600; }
      }
    }
    .modal-overlay { position:fixed; inset:0; background:rgba(0,0,0,.5); z-index:200; display:flex; align-items:center; justify-content:center; padding:16px; }
    .modal-card { max-width:440px; width:100%; h3{margin:0 0 20px;} .pix-qr{display:block;margin:0 auto 16px;max-width:220px;border-radius:8px;} .pix-label{margin:0 0 4px;font-size:13px;font-weight:500;} }
  `],
})
export class PlansComponent implements OnInit {
  plans        = signal<Plan[]>([]);
  loading      = signal(true);
  subscribing  = signal<number | null>(null);
  pixResult    = signal<SubscribeResponse | null>(null);
  boletoResult = signal<SubscribeResponse | null>(null);
  copied       = signal(false);
  selectedMethod = signal<'pix' | 'boleto'>('pix');

  readonly methods = [
    { value: 'pix'    as const, icon: '⚡', label: 'Pix'    },
    { value: 'boleto' as const, icon: '📄', label: 'Boleto' },
  ];

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.http.get<Plan[]>('/api/v1/plans').subscribe({
      next:  (p: Plan[]) => { this.plans.set(p); this.loading.set(false); },
      error: ()           => this.loading.set(false),
    });
  }

  subscribe(plan: Plan) {
    this.subscribing.set(plan.id);
    this.http.post<SubscribeResponse>('/api/v1/payments/subscribe', {
      planName: plan.name, priceCents: plan.priceCents,
      currency: plan.currency, paymentMethod: this.selectedMethod(),
    }).subscribe({
      next: (res: SubscribeResponse) => {
        this.subscribing.set(null);
        if (res.pixCode || res.pixQrCode) this.pixResult.set(res);
        else if (res.boletoUrl)           this.boletoResult.set(res);
      },
      error: (e: { error?: { message?: string } }) => {
        this.subscribing.set(null);
        alert(e.error?.message ?? 'Erro ao assinar');
      },
    });
  }

  copyPix() {
    const code = this.pixResult()?.pixCode;
    if (code) navigator.clipboard.writeText(code).then(() => {
      this.copied.set(true); setTimeout(() => this.copied.set(false), 2000);
    });
  }
}

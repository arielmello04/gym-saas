import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PartnerApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { PartnerHealth, PartnerLink, PartnerTenantConfig, PendingPartnerCheckin } from '../../../core/models';
import { IconComponent } from '../../../shared/icon.component';

@Component({
  selector: 'app-admin-partners',
  standalone: true,
  imports: [DatePipe, FormsModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="page-title">
        <h1>Parceiros</h1>
        <span class="page-subtitle">Wellhub e TotalPass: entradas recebidas e identidade dos alunos.</span>
      </div>
      <button class="btn btn-secondary btn-sm" (click)="load()">Atualizar</button>
    </div>

    <div class="section-title">Conexão com os parceiros</div>

    <div class="stat-grid">
      @for (h of health(); track h.provider) {
        <div class="stat enter" [class.stat-accent]="h.credentialsValid && h.mode === 'live'">
          <span class="stat-label">{{ h.provider }}</span>
          <span class="stat-value" style="font-size:19px" [style.color]="cor(h)">{{ rotulo(h) }}</span>
          <span class="stat-hint">{{ h.detail }}</span>
        </div>
      }
      @if (health().length === 0) {
        <div class="skeleton" style="height:110px"></div>
      }
    </div>

    <div class="card creds">
      <h3>Credenciais desta academia</h3>
      <p class="muted hint">O token do Wellhub e a <code>partner_api_key</code> da TotalPass são da
        Crivo e ficam em variável de ambiente. O que muda de academia para academia é o que está aqui:
        o <strong>Gym ID</strong> (Wellhub) e a <strong>place_api_key</strong> (TotalPass, gerada pela
        própria academia no portal deles).</p>

      <div class="cred-row">
        <span class="cred-label">Wellhub · Gym ID</span>
        <input [(ngModel)]="cred.gymId" name="gymId" placeholder="ex.: 12345"
               (keyup.enter)="salvarCred('WELLHUB')">
        <button class="btn btn-secondary btn-sm" [disabled]="savingCred() === 'WELLHUB'"
                (click)="salvarCred('WELLHUB')">
          @if (savingCred() === 'WELLHUB') { <span class="spinner spinner-sm"></span> }
          Salvar
        </button>
      </div>

      <div class="cred-row">
        <span class="cred-label">TotalPass · place_api_key</span>
        <input [(ngModel)]="cred.placeApiKey" name="placeApiKey" type="password"
               [placeholder]="atual('TOTALPASS')?.placeApiKeyMasked ?? 'chave gerada no portal'"
               (keyup.enter)="salvarCred('TOTALPASS')">
        <button class="btn btn-secondary btn-sm" [disabled]="savingCred() === 'TOTALPASS'"
                (click)="salvarCred('TOTALPASS')">
          @if (savingCred() === 'TOTALPASS') { <span class="spinner spinner-sm"></span> }
          Salvar
        </button>
      </div>
    </div>

    <div class="section-title">Entradas aguardando liberação</div>

    @if (loadingPending()) {
      <div class="rows">
        @for (i of [1,2]; track i) { <div class="skeleton" style="height:74px"></div> }
      </div>
    } @else if (pending().length === 0) {
      <div class="empty-state">
        <div class="icon"><app-icon name="clock" [size]="40" [stroke]="1.4" /></div>
        <h3>Nenhuma entrada aguardando</h3>
        <p>Quando um aluno fizer check-in no aplicativo do TotalPass, a entrada aparece aqui
           para você liberar. O parceiro dá 90 minutos.</p>
      </div>
    } @else {
      <div class="rows">
        @for (e of pending(); track e.id) {
          <div class="row enter">
            <div class="row-main">
              <span class="row-title">
                {{ e.userName ?? 'Sem nome' }}
                <span class="badge badge-neutral">{{ e.provider }}</span>
              </span>
              <span class="row-sub nums">
                @if (e.userEmail) { {{ e.userEmail }} }
                @else { <span class="warn">CPF {{ e.document }} sem cadastro vinculado</span> }
                @if (e.planCode) { <span class="dim">· plano {{ e.planCode }}</span> }
              </span>
              <span class="row-sub nums dim">Expira {{ e.expiresAt | date:'HH:mm' }}</span>
            </div>
            <div class="row-side">
              @if (!e.userEmail) {
                <input class="inline-input" [ngModel]="emailFor()[e.id] ?? ''"
                       (ngModelChange)="setEmail(e.id, $event)"
                       [name]="'email-' + e.id" placeholder="e-mail do aluno">
              }
              <button class="btn btn-primary btn-sm" [disabled]="busy() === e.id" (click)="confirm(e)">
                @if (busy() === e.id) { <span class="spinner spinner-sm"></span> }
                Liberar
              </button>
            </div>
          </div>
        }
      </div>
    }

    <div class="section-title">Identidade dos alunos nos parceiros</div>

    <div class="card add">
      <h3>Vincular aluno</h3>
      <p class="muted hint">O parceiro identifica a pessoa por um id do lado dele. Sem o vínculo,
        a entrada não tem a quem ser atribuída.</p>
      <div class="add-form">
        <select [(ngModel)]="form.provider" name="provider" aria-label="Parceiro">
          <option value="WELLHUB">Wellhub</option>
          <option value="TOTALPASS">TotalPass</option>
        </select>
        <input [(ngModel)]="form.email" name="linkEmail" type="email" placeholder="e-mail do aluno">
        <input [(ngModel)]="form.externalId" name="externalId"
               [placeholder]="form.provider === 'WELLHUB' ? 'Wellhub ID (13 dígitos)' : 'código do usuário'">
        <input [(ngModel)]="form.document" name="document" placeholder="CPF (TotalPass)">
        <input [(ngModel)]="form.customCode" name="customCode" placeholder="PIN/QR (Wellhub)">
        <button class="btn btn-primary" [disabled]="saving()" (click)="save()">
          @if (saving()) { <span class="spinner spinner-sm"></span> } @else { <app-icon name="plus" [size]="16" /> }
          Salvar
        </button>
      </div>
    </div>

    @if (loadingLinks()) {
      <div class="rows">
        @for (i of [1,2]; track i) { <div class="skeleton" style="height:58px"></div> }
      </div>
    } @else if (links().length > 0) {
      <div class="rows">
        @for (l of links(); track l.id) {
          <div class="row enter">
            <div class="row-main">
              <span class="row-title truncate">{{ l.email }}</span>
              <span class="row-sub nums">
                {{ l.provider }} · {{ l.externalId }}
                @if (l.document) { <span class="dim">· CPF {{ l.document }}</span> }
                @if (l.customCode) { <span class="dim">· {{ l.customCode }}</span> }
              </span>
            </div>
            <button class="btn btn-danger btn-sm" (click)="removeLink(l)">Remover</button>
          </div>
        }
      </div>
    }

    <div class="section-title">Webhook da TotalPass</div>

    <div class="card hook">
      <p class="muted">Registre este endereço no portal da TotalPass para receber os check-ins.
        A URL carrega a academia e um segredo — trate como credencial.</p>

      @if (webhook(); as w) {
        <code class="url">{{ w.url }}</code>
        @if (!w.secretConfigured) {
          <div class="alert alert-danger">
            O segredo do webhook está no valor padrão. Defina
            <code>CHECKIN_WEBHOOK_SECRET</code> antes de usar em produção.
          </div>
        }
        <div class="inline">
          <button class="btn btn-secondary btn-sm" (click)="copy(w.url)">Copiar</button>
          <button class="btn btn-primary btn-sm" [disabled]="registering()" (click)="register()">
            @if (registering()) { <span class="spinner spinner-sm"></span> }
            Registrar na TotalPass
          </button>
        </div>
      }
    </div>
  `,
  styles: [`
    .creds { margin-bottom: 18px; }
    .creds h3 { margin-bottom: 4px; }
    .cred-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 10px; }
    .cred-label { min-width: 210px; font-size: 13px; font-weight: 600; color: var(--text-muted); }
    .cred-row input { flex: 1; min-width: 200px; }
    .add { margin-bottom: 18px; }
    .add h3 { margin-bottom: 4px; }
    .hint { font-size: 13px; margin-bottom: 14px; }
    .add-form { display: flex; gap: 10px; flex-wrap: wrap; }
    .add-form input { flex: 1; min-width: 170px; }
    .add-form select { width: auto; min-width: 130px; }
    .inline-input { width: 210px; }
    .warn { color: var(--warning); }
    .hook { display: flex; flex-direction: column; gap: 12px; }
    .url { display: block; padding: 10px 12px; background: var(--surface-hi); border: 1px solid var(--border); border-radius: var(--r-sm); font-size: 12px; word-break: break-all; }
  `],
})
export class AdminPartnersComponent implements OnInit, OnDestroy {
  private api = inject(PartnerApiService);
  private toast = inject(ToastService);

  pending = signal<PendingPartnerCheckin[]>([]);
  links = signal<PartnerLink[]>([]);
  webhook = signal<{ url: string; secretConfigured: boolean } | null>(null);
  health = signal<PartnerHealth[]>([]);
  configs = signal<PartnerTenantConfig[]>([]);
  savingCred = signal<string | null>(null);

  cred = { gymId: '', placeApiKey: '' };

  loadingPending = signal(true);
  loadingLinks = signal(true);
  saving = signal(false);
  registering = signal(false);
  busy = signal<number | null>(null);

  emailFor = signal<Record<number, string>>({});

  form = { provider: 'WELLHUB' as 'WELLHUB' | 'TOTALPASS', email: '', externalId: '', document: '', customCode: '' };

  /** A entrada chega por webhook: sem recarregar, a recepção não a veria. */
  private timer?: ReturnType<typeof setInterval>;

  ngOnInit() {
    this.load();
    this.timer = setInterval(() => this.loadPending(), 20_000);
  }

  ngOnDestroy() {
    if (this.timer) clearInterval(this.timer);
  }

  load() {
    this.loadPending();
    this.loadLinks();
    this.api.webhookUrl().subscribe({ next: w => this.webhook.set(w), error: () => {} });
    this.api.diagnostics().subscribe({ next: h => this.health.set(h), error: () => {} });
    this.loadConfigs();
  }

  atual(provider: string) {
    return this.configs().find(c => c.provider === provider) ?? null;
  }

  salvarCred(provider: 'WELLHUB' | 'TOTALPASS') {
    const valor = provider === 'WELLHUB' ? this.cred.gymId.trim() : this.cred.placeApiKey.trim();
    if (!valor) {
      this.toast.error(provider === 'WELLHUB'
        ? 'Informe o Gym ID desta academia.'
        : 'Informe a place_api_key desta academia.');
      return;
    }

    this.savingCred.set(provider);
    this.api.upsertConfig({
      provider,
      gymId: provider === 'WELLHUB' ? valor : undefined,
      placeApiKey: provider === 'TOTALPASS' ? valor : undefined,
    }).subscribe({
      next: () => {
        this.savingCred.set(null);
        // A chave nunca volta inteira do servidor; limpar evita a impressão
        // de que o campo mostra o que está guardado.
        if (provider === 'TOTALPASS') this.cred.placeApiKey = '';
        this.toast.success('Credencial salva.');
        this.load();
      },
      error: e => {
        this.savingCred.set(null);
        this.toast.fromApi(e, 'Não foi possível salvar a credencial.');
      },
    });
  }

  private loadConfigs() {
    this.api.configs().subscribe({
      next: c => {
        this.configs.set(c);
        this.cred.gymId = c.find(x => x.provider === 'WELLHUB')?.gymId ?? '';
      },
      error: () => {},
    });
  }

  /** Em mock, o verde seria mentira: nada foi testado contra o parceiro. */
  rotulo(h: PartnerHealth) {
    if (h.mode === 'mock') return 'Modo teste';
    if (!h.configured) return 'Falta configurar';
    if (!h.reachable) return 'Inacessível';
    return h.credentialsValid ? 'Conectado' : 'Credencial recusada';
  }

  cor(h: PartnerHealth) {
    if (h.mode === 'mock') return 'var(--text-muted)';
    if (h.credentialsValid) return 'var(--success)';
    return h.configured ? 'var(--danger)' : 'var(--warning)';
  }

  setEmail(id: number, email: string) {
    this.emailFor.update(m => ({ ...m, [id]: email }));
  }

  confirm(e: PendingPartnerCheckin) {
    const email = e.userEmail ? undefined : (this.emailFor()[e.id] ?? '').trim();
    if (!e.userEmail && !email) {
      this.toast.error('Informe o e-mail do aluno para vincular o CPF.');
      return;
    }

    this.busy.set(e.id);
    this.api.confirm(e.id, email || undefined).subscribe({
      next: r => {
        this.busy.set(null);
        if (r.status === 'CONFIRMED') {
          this.toast.success('Entrada liberada.');
        } else {
          this.toast.error(r.failureReason ?? 'O parceiro não confirmou a entrada.');
        }
        this.load();
      },
      error: err => {
        this.busy.set(null);
        this.toast.fromApi(err, 'Não foi possível liberar a entrada.');
      },
    });
  }

  save() {
    if (!this.form.email.trim() || !this.form.externalId.trim()) {
      this.toast.error('Informe o e-mail do aluno e o identificador no parceiro.');
      return;
    }
    this.saving.set(true);
    this.api.upsertLink({
      provider: this.form.provider,
      email: this.form.email.trim(),
      externalId: this.form.externalId.trim(),
      document: this.form.document.trim() || undefined,
      customCode: this.form.customCode.trim() || undefined,
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.form = { provider: this.form.provider, email: '', externalId: '', document: '', customCode: '' };
        this.toast.success('Vínculo salvo.');
        this.loadLinks();
      },
      error: e => {
        this.saving.set(false);
        this.toast.fromApi(e, 'Não foi possível salvar o vínculo.');
      },
    });
  }

  removeLink(l: PartnerLink) {
    this.api.removeLink(l.id).subscribe({
      next: () => { this.toast.info('Vínculo removido.'); this.loadLinks(); },
      error: e => this.toast.fromApi(e, 'Não foi possível remover o vínculo.'),
    });
  }

  register() {
    this.registering.set(true);
    this.api.registerWebhook().subscribe({
      next: r => {
        this.registering.set(false);
        r.registered
          ? this.toast.success('Webhook registrado na TotalPass.')
          : this.toast.error('A TotalPass não aceitou o registro. Confira as credenciais.');
      },
      error: e => {
        this.registering.set(false);
        this.toast.fromApi(e, 'Não foi possível registrar o webhook.');
      },
    });
  }

  copy(url: string) {
    navigator.clipboard.writeText(url).then(
      () => this.toast.success('URL copiada.'),
      () => this.toast.error('Não foi possível copiar.'));
  }

  private loadPending() {
    this.api.pending().subscribe({
      next: p => { this.pending.set(p); this.loadingPending.set(false); },
      error: () => this.loadingPending.set(false),
    });
  }

  private loadLinks() {
    this.api.links().subscribe({
      next: l => { this.links.set(l); this.loadingLinks.set(false); },
      error: () => this.loadingLinks.set(false),
    });
  }
}

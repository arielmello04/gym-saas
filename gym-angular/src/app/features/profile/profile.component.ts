import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { MeApiService, ProfileApiService } from '../../core/services/api.service';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/services/auth.service';
import { MeResponse, ProfilePreferences } from '../../core/models';

interface PrefRow {
  key: keyof ProfilePreferences;
  title: string;
  hint: string;
}

@Component({
  selector: 'app-profile',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="page-title">
        <h1>Perfil</h1>
        <span class="page-subtitle">Seus dados e preferências de privacidade.</span>
      </div>
    </div>

    <div class="card ident">
      <div class="avatar">{{ initial() }}</div>
      <div class="ident-text">
        <span class="ident-email">{{ email() }}</span>
        <div class="inline">
          <span class="badge badge-accent">{{ roleLabel() }}</span>
          @if (me(); as m) {
            <span class="badge" [class]="m.subscriptionStatus === 'ACTIVE' ? 'badge-success' : 'badge-warning'">
              {{ m.subscriptionStatus === 'ACTIVE' ? 'Assinatura ativa'
                 : m.subscriptionStatus === 'PAST_DUE' ? 'Assinatura em atraso' : 'Sem assinatura' }}
            </span>
          }
        </div>
      </div>
    </div>

    <div class="section-title">Privacidade</div>

    @if (loading()) {
      <div class="skeleton" style="height:210px;max-width:520px"></div>
    } @else {
      <div class="card prefs">
        <p class="muted intro">Controla o uso da sua imagem em fotos e vídeos feitos na academia.</p>

        @for (row of rows; track row.key) {
          <div class="switch">
            <span class="switch-text">
              <strong>{{ row.title }}</strong>
              <span>{{ row.hint }}</span>
            </span>
            <button class="switch-btn" role="switch"
                    [attr.aria-checked]="prefs()[row.key]"
                    [attr.aria-label]="row.title"
                    (click)="toggle(row.key)"></button>
          </div>
        }

        <button class="btn btn-primary" style="margin-top:20px" [disabled]="saving() || !dirty()" (click)="save()">
          @if (saving()) { <span class="spinner spinner-sm"></span> }
          {{ dirty() ? 'Salvar alterações' : 'Tudo salvo' }}
        </button>
      </div>
    }
  `,
  styles: [`
    .ident { display: flex; align-items: center; gap: 16px; max-width: 520px; }
    .avatar { display: grid; place-items: center; width: 52px; height: 52px; flex: none; border-radius: 50%; background: var(--accent); color: var(--accent-ink); font-size: 21px; font-weight: 800; }
    .ident-text { display: flex; flex-direction: column; gap: 7px; min-width: 0; }
    .ident-email { font-size: 15px; font-weight: 600; word-break: break-all; }
    .prefs { max-width: 520px; }
    .intro { font-size: 13px; margin-bottom: 6px; }
  `],
})
export class ProfileComponent implements OnInit {
  private api = inject(ProfileApiService);
  private meApi = inject(MeApiService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  readonly rows: PrefRow[] = [
    { key: 'allowRecording',      title: 'Gravações em vídeo',  hint: 'Aulas gravadas podem incluir você.' },
    { key: 'allowPhotos',         title: 'Fotos',               hint: 'Fotos da academia podem incluir você.' },
    { key: 'allowFaceVisibility', title: 'Rosto visível',       hint: 'Seu rosto pode aparecer sem desfoque.' },
  ];

  prefs = signal<ProfilePreferences>({ allowRecording: false, allowPhotos: false, allowFaceVisibility: false });
  me = signal<MeResponse | null>(null);
  loading = signal(true);
  saving = signal(false);

  private saved = signal<ProfilePreferences | null>(null);

  /** Só habilita o botão quando há algo de fato para gravar. */
  dirty = computed(() => {
    const base = this.saved();
    if (!base) return false;
    const now = this.prefs();
    return this.rows.some(r => base[r.key] !== now[r.key]);
  });

  email = computed(() => this.auth.currentUser()?.sub ?? '');
  initial = computed(() => (this.email()[0] ?? '?').toUpperCase());
  roleLabel = computed(() => {
    const r = this.auth.tenantRole() ?? this.auth.currentUser()?.role ?? '';
    const map: Record<string, string> = {
      OWNER: 'Dono', MANAGER: 'Gerente', STAFF: 'Recepção',
      TRAINER: 'Professor', MEMBER: 'Aluno', ADMIN_WEB: 'Admin', ADMIN_APP: 'Admin', USER: 'Aluno',
    };
    return map[r] ?? r;
  });

  ngOnInit() {
    this.api.get().subscribe({
      next: p => { this.prefs.set({ ...p }); this.saved.set({ ...p }); this.loading.set(false); },
      error: e => {
        this.loading.set(false);
        this.toast.fromApi(e, 'Não foi possível carregar suas preferências.');
      },
    });
    this.meApi.get().subscribe({ next: m => this.me.set(m), error: () => {} });
  }

  toggle(key: keyof ProfilePreferences) {
    this.prefs.update(p => ({ ...p, [key]: !p[key] }));
  }

  save() {
    this.saving.set(true);
    this.api.update(this.prefs()).subscribe({
      next: p => {
        this.saving.set(false);
        this.prefs.set({ ...p });
        this.saved.set({ ...p });
        this.toast.success('Preferências salvas.');
      },
      error: e => {
        this.saving.set(false);
        this.toast.fromApi(e, 'Não foi possível salvar as preferências.');
      },
    });
  }
}

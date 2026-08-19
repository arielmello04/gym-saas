import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { BookingApiService, MeApiService, WaitlistApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { AvailabilityItem, MeResponse } from '../../../core/models';
import { IconComponent } from '../../../shared/icon.component';

@Component({
  selector: 'app-availability',
  standalone: true,
  imports: [DatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="page-title">
        <h1>Agenda</h1>
        <span class="page-subtitle">{{ weekLabel() }}</span>
      </div>
      <div class="inline">
        <button class="btn btn-secondary btn-sm" (click)="shiftWeek(-1)" aria-label="Semana anterior">
          <app-icon name="arrow-left" [size]="16" />
        </button>
        <button class="btn btn-secondary btn-sm" (click)="thisWeek()">Hoje</button>
        <button class="btn btn-secondary btn-sm" (click)="shiftWeek(1)" aria-label="Próxima semana">
          <app-icon name="arrow-right" [size]="16" />
        </button>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat stat-accent enter">
        <span class="stat-label">Aulas na semana</span>
        <span class="stat-value nums">{{ sessions().length }}</span>
        <span class="stat-hint">{{ openSessions() }} com vaga aberta</span>
      </div>
      <div class="stat enter">
        <span class="stat-label">Vagas livres</span>
        <span class="stat-value nums">{{ totalSpots() }}</span>
        <span class="stat-hint">somando toda a semana</span>
      </div>
      <div class="stat enter">
        <span class="stat-label">Próxima aula</span>
        <span class="stat-value nums">{{ nextSession() ? (nextSession()!.startAt | date:'dd/MM') : '—' }}</span>
        <span class="stat-hint">{{ nextSession() ? nextSession()!.classTypeName + ' · ' + (nextSession()!.startAt | date:'HH:mm') : 'nada agendado' }}</span>
      </div>
      <div class="stat enter">
        <span class="stat-label">Assinatura</span>
        <span class="stat-value" [style.color]="subActive() ? 'var(--success)' : 'var(--warning)'"
              style="font-size:22px">{{ subLabel() }}</span>
        <span class="stat-hint">necessária para reservar</span>
      </div>
    </div>

    <div class="section-title">Aulas disponíveis</div>

    @if (loading()) {
      <div class="grid-cards">
        @for (i of [1,2,3]; track i) { <div class="skeleton" style="height:150px"></div> }
      </div>
    } @else if (sessions().length === 0) {
      <div class="empty-state">
        <div class="icon"><app-icon name="calendar" [size]="40" [stroke]="1.4" /></div>
        <h3>Nenhuma aula nesta semana</h3>
        <p>A academia ainda não publicou a agenda deste período. Tente a próxima semana.</p>
        <button class="btn btn-secondary" (click)="shiftWeek(1)">Ver próxima semana</button>
      </div>
    } @else {
      <div class="grid-cards">
        @for (s of sessions(); track s.sessionId) {
          <article class="session enter" [class.is-full]="s.spotsLeft === 0">
            <header class="spread">
              <span class="s-name">{{ s.classTypeName }}</span>
              @if (s.spotsLeft === 0) {
                <span class="badge badge-danger">Lotada</span>
              } @else if (s.spotsLeft <= 3) {
                <span class="badge badge-warning">{{ s.spotsLeft }} vaga{{ s.spotsLeft > 1 ? 's' : '' }}</span>
              } @else {
                <span class="badge badge-accent">{{ s.spotsLeft }} vagas</span>
              }
            </header>

            <div class="s-when nums">{{ s.startAt | date:'EEE, dd/MM' }} · {{ s.startAt | date:'HH:mm' }}</div>

            <div class="s-fill" [attr.aria-label]="'Ocupação ' + taken(s) + ' de ' + s.capacity">
              <div class="s-fill-bar" [style.width.%]="fillPct(s)"></div>
            </div>
            <div class="s-meta nums">
              <span>{{ taken(s) }}/{{ s.capacity }} ocupadas</span>
              <span>{{ durationMin(s) }} min</span>
            </div>

            @if (s.notes) { <p class="s-notes">{{ s.notes }}</p> }

            @if (s.spotsLeft > 0) {
              <button class="btn btn-primary btn-sm btn-full" [disabled]="pending() === s.sessionId" (click)="book(s)">
                @if (pending() === s.sessionId) { <span class="spinner spinner-sm"></span> }
                Reservar
              </button>
            } @else {
              <button class="btn btn-secondary btn-sm btn-full" [disabled]="pending() === s.sessionId" (click)="joinWaitlist(s)">
                @if (pending() === s.sessionId) { <span class="spinner spinner-sm"></span> }
                Entrar na fila
              </button>
            }
          </article>
        }
      </div>
    }
  `,
  styles: [`
    .session { display: flex; flex-direction: column; gap: 10px; padding: 18px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--r-lg); transition: border-color var(--t), transform var(--t); }
    .session:hover { border-color: var(--border-strong); transform: translateY(-2px); }
    .session.is-full { opacity: .72; }
    .s-name { font-weight: 700; font-size: 15px; }
    .s-when { font-size: 13px; color: var(--text-muted); text-transform: capitalize; }
    .s-fill { height: 5px; background: var(--surface-hi-2); border-radius: var(--r-pill); overflow: hidden; }
    .s-fill-bar { height: 100%; background: var(--accent); border-radius: inherit; transition: width var(--t-slow); }
    .is-full .s-fill-bar { background: var(--danger); }
    .s-meta { display: flex; justify-content: space-between; font-size: 12px; color: var(--text-dim); }
    .s-notes { font-size: 13px; color: var(--text-muted); padding: 8px 10px; background: var(--surface-hi); border-radius: var(--r-sm); }
  `],
})
export class AvailabilityComponent implements OnInit {
  private booking = inject(BookingApiService);
  private waitlist = inject(WaitlistApiService);
  private meApi = inject(MeApiService);
  private toast = inject(ToastService);

  sessions = signal<AvailabilityItem[]>([]);
  loading = signal(true);
  pending = signal<number | null>(null);
  me = signal<MeResponse | null>(null);

  private weekStart = signal(mondayOf(new Date()));

  openSessions = computed(() => this.sessions().filter(s => s.spotsLeft > 0).length);
  totalSpots = computed(() => this.sessions().reduce((sum, s) => sum + s.spotsLeft, 0));
  nextSession = computed(() => {
    const now = Date.now();
    return this.sessions()
      .filter(s => new Date(s.startAt).getTime() > now)
      .sort((a, b) => a.startAt.localeCompare(b.startAt))[0] ?? null;
  });
  subActive = computed(() => this.me()?.subscriptionStatus === 'ACTIVE');
  subLabel = computed(() => {
    const s = this.me()?.subscriptionStatus;
    if (s === 'ACTIVE') return 'Ativa';
    if (s === 'PAST_DUE') return 'Em atraso';
    return 'Sem plano';
  });

  weekLabel = computed(() => {
    const start = this.weekStart();
    const end = new Date(start);
    end.setDate(end.getDate() + 6);
    const f = (d: Date) => d.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' });
    return `${f(start)} — ${f(end)}`;
  });

  ngOnInit() {
    this.load();
    this.meApi.get().subscribe({ next: m => this.me.set(m), error: () => {} });
  }

  shiftWeek(weeks: number) {
    const d = new Date(this.weekStart());
    d.setDate(d.getDate() + weeks * 7);
    this.weekStart.set(d);
    this.load();
  }

  thisWeek() {
    this.weekStart.set(mondayOf(new Date()));
    this.load();
  }

  taken(s: AvailabilityItem) { return s.capacity - s.spotsLeft; }
  fillPct(s: AvailabilityItem) { return s.capacity ? (this.taken(s) / s.capacity) * 100 : 0; }
  durationMin(s: AvailabilityItem) {
    return Math.round((new Date(s.endAt).getTime() - new Date(s.startAt).getTime()) / 60000);
  }

  book(s: AvailabilityItem) {
    this.pending.set(s.sessionId);
    this.booking.book(s.sessionId).subscribe({
      next: () => {
        this.toast.success(`Reserva confirmada em ${s.classTypeName}.`);
        this.load();
      },
      error: e => {
        this.pending.set(null);
        this.toast.fromApi(e, 'Não foi possível reservar esta aula.');
      },
    });
  }

  joinWaitlist(s: AvailabilityItem) {
    this.pending.set(s.sessionId);
    this.waitlist.join(s.sessionId).subscribe({
      next: r => {
        this.pending.set(null);
        this.toast.success(`Você é o ${r.position}º da fila em ${s.classTypeName}.`);
      },
      error: e => {
        this.pending.set(null);
        this.toast.fromApi(e, 'Não foi possível entrar na fila.');
      },
    });
  }

  private load() {
    this.loading.set(true);
    const from = this.weekStart().toISOString();
    const to = new Date(this.weekStart().getTime() + 7 * 86_400_000).toISOString();
    this.booking.getAvailability(from, to).subscribe({
      next: s => { this.sessions.set(s); this.loading.set(false); this.pending.set(null); },
      error: e => {
        this.loading.set(false);
        this.pending.set(null);
        this.toast.fromApi(e, 'Não foi possível carregar a agenda.');
      },
    });
  }
}

/** Segunda-feira da semana da data informada. */
function mondayOf(d: Date): Date {
  const copy = new Date(d);
  const day = copy.getDay();
  copy.setDate(copy.getDate() - day + (day === 0 ? -6 : 1));
  copy.setHours(0, 0, 0, 0);
  return copy;
}

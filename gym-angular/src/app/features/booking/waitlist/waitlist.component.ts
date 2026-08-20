import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WaitlistApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { WaitlistEntry } from '../../../core/models';
import { IconComponent } from '../../../shared/icon.component';

@Component({
  selector: 'app-waitlist',
  standalone: true,
  imports: [DatePipe, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="page-title">
        <h1>Fila de espera</h1>
        <span class="page-subtitle">Aulas lotadas em que você está aguardando vaga.</span>
      </div>
    </div>

    @if (loading()) {
      <div class="rows">
        @for (i of [1,2]; track i) { <div class="skeleton" style="height:110px"></div> }
      </div>
    } @else if (entries().length === 0) {
      <div class="empty-state">
        <div class="icon"><app-icon name="clock" [size]="40" [stroke]="1.4" /></div>
        <h3>Você não está em nenhuma fila</h3>
        <p>Quando uma aula estiver lotada, entre na fila pela agenda e avisamos assim que abrir vaga.</p>
        <a routerLink="/dashboard" class="btn btn-primary">Ver agenda</a>
      </div>
    } @else {
      <div class="stack">
        @for (e of entries(); track e.entryId) {
          <article class="entry enter" [class.promoted]="e.status === 'PROMOTED'">
            @if (e.status === 'PROMOTED') {
              <div class="call">
                <app-icon name="check" [size]="17" />
                <span>Vaga liberada. Confirme até {{ e.expiresAt | date:'dd/MM HH:mm' }} ou ela passa para o próximo.</span>
              </div>
            }

            <div class="spread">
              <div class="row-main">
                <span class="row-title">{{ e.sessionType }}</span>
                <span class="row-sub nums">{{ e.sessionStartAt | date:'EEEE, dd/MM · HH:mm' }}</span>
              </div>
              @if (e.status === 'WAITING') {
                <div class="pos">
                  <span class="pos-value nums">{{ e.position }}º</span>
                  <span class="pos-label nums">de {{ e.totalWaiting }}</span>
                </div>
              } @else {
                <span class="badge" [class]="badgeClass(e.status)">{{ statusLabel(e.status) }}</span>
              }
            </div>

            <div class="inline actions">
              @if (e.status === 'PROMOTED') {
                <button class="btn btn-primary btn-sm" [disabled]="pending() === e.sessionId" (click)="confirm(e)">
                  @if (pending() === e.sessionId) { <span class="spinner spinner-sm"></span> }
                  Confirmar vaga
                </button>
              }
              <button class="btn btn-ghost btn-sm" [disabled]="pending() === e.sessionId" (click)="leave(e)">
                Sair da fila
              </button>
            </div>
          </article>
        }
      </div>
    }
  `,
  styles: [`
    .entry { display: flex; flex-direction: column; gap: 14px; padding: 18px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--r-lg); }
    .entry.promoted { border-color: var(--success); }
    .call { display: flex; align-items: center; gap: 9px; padding: 10px 12px; background: var(--success-soft); color: var(--success); border-radius: var(--r-sm); font-size: 13px; font-weight: 600; }
    .row-sub { text-transform: capitalize; }
    .pos { display: flex; flex-direction: column; align-items: flex-end; }
    .pos-value { font-size: 26px; font-weight: 800; letter-spacing: -.04em; line-height: 1; color: var(--accent); }
    .pos-label { font-size: 12px; color: var(--text-dim); }
    .actions { justify-content: flex-end; }
  `],
})
export class WaitlistComponent implements OnInit {
  private api = inject(WaitlistApiService);
  private toast = inject(ToastService);

  entries = signal<WaitlistEntry[]>([]);
  loading = signal(true);
  pending = signal<number | null>(null);

  ngOnInit() { this.load(); }

  statusLabel(s: string) {
    return s === 'PROMOTED' ? 'Vaga liberada' : s === 'EXPIRED' ? 'Prazo vencido' : s === 'CANCELED' ? 'Saiu da fila' : s;
  }
  badgeClass(s: string) {
    return s === 'PROMOTED' ? 'badge-success' : s === 'EXPIRED' ? 'badge-warning' : 'badge-neutral';
  }

  confirm(e: WaitlistEntry) {
    this.pending.set(e.sessionId);
    this.api.confirm(e.sessionId).subscribe({
      next: () => {
        this.pending.set(null);
        this.toast.success(`Vaga confirmada em ${e.sessionType}.`);
        this.load();
      },
      error: err => {
        this.pending.set(null);
        this.toast.fromApi(err, 'Não foi possível confirmar a vaga.');
        this.load();
      },
    });
  }

  leave(e: WaitlistEntry) {
    this.pending.set(e.sessionId);
    this.api.leave(e.sessionId).subscribe({
      next: () => {
        this.pending.set(null);
        this.toast.info('Você saiu da fila.');
        this.load();
      },
      error: err => {
        this.pending.set(null);
        this.toast.fromApi(err, 'Não foi possível sair da fila.');
      },
    });
  }

  private load() {
    this.loading.set(true);
    this.api.myEntries().subscribe({
      next: e => { this.entries.set(e); this.loading.set(false); },
      error: e => {
        this.loading.set(false);
        this.toast.fromApi(e, 'Não foi possível carregar suas filas.');
      },
    });
  }
}

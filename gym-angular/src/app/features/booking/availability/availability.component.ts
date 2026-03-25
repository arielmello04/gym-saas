import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { BookingApiService, WaitlistApiService } from '../../../core/services/api.service';
import { AvailabilityItem } from '../../../core/models';

@Component({
  selector: 'app-availability',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="page-header"><h1>📅 Agenda de Aulas</h1></div>

    <div class="week-nav">
      <button class="btn btn-secondary btn-sm" (click)="prevWeek()">← Anterior</button>
      <span class="week-label">{{ weekLabel() }}</span>
      <button class="btn btn-secondary btn-sm" (click)="nextWeek()">Próxima →</button>
    </div>

    @if (loading()) {
      <div style="display:flex;justify-content:center;padding:48px"><div class="spinner"></div></div>
    } @else if (sessions().length === 0) {
      <div class="empty-state">
        <div class="icon">📭</div><h3>Nenhuma aula disponível</h3>
        <p>Não há aulas programadas para esta semana.</p>
      </div>
    } @else {
      <div class="sessions-grid">
        @for (s of sessions(); track s.sessionId) {
          <div class="session-card card" [class.full]="s.spotsLeft === 0">
            <div class="session-header">
              <span class="session-type">{{ s.classTypeName }}</span>
              @if (s.spotsLeft === 0) { <span class="badge badge-danger">Lotada</span> }
              @else if (s.spotsLeft <= 3) { <span class="badge badge-warning">{{ s.spotsLeft }} vaga(s)</span> }
              @else { <span class="badge badge-success">{{ s.spotsLeft }} vagas</span> }
            </div>
            <div class="session-meta">
              <span>🕐 {{ s.startAt | date:'EEE dd/MM HH:mm' }}</span>
              <span>⏱ {{ durationMin(s) }}min</span>
              <span>👥 {{ s.capacity - s.spotsLeft }}/{{ s.capacity }}</span>
            </div>
            @if (s.notes) { <p class="session-notes">{{ s.notes }}</p> }
            <div class="session-actions">
              @if (s.spotsLeft > 0) {
                <button class="btn btn-primary btn-sm" [disabled]="pending() === s.sessionId" (click)="book(s)">
                  @if (pending() === s.sessionId) { <span class="spinner" style="width:14px;height:14px"></span> }
                  Reservar
                </button>
              } @else {
                <button class="btn btn-secondary btn-sm" [disabled]="pending() === s.sessionId" (click)="joinWaitlist(s)">
                  Entrar na fila
                </button>
              }
            </div>
            @if (feedback()[s.sessionId]) {
              <p class="feedback" [class.success]="feedback()[s.sessionId]!.ok" [class.error]="!feedback()[s.sessionId]!.ok">
                {{ feedback()[s.sessionId]!.msg }}
              </p>
            }
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .week-nav { display:flex; align-items:center; gap:16px; margin-bottom:24px; .week-label{font-weight:600;font-size:15px;flex:1;text-align:center;} }
    .sessions-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(300px,1fr)); gap:16px; }
    .session-card { padding:16px; &.full{opacity:.8;}
      .session-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; .session-type{font-weight:600;font-size:15px;} }
      .session-meta { display:flex; gap:12px; flex-wrap:wrap; font-size:13px; color:var(--color-text-muted); margin-bottom:12px; }
      .session-notes { font-size:13px; color:var(--color-text-muted); margin:0 0 12px; }
      .session-actions { display:flex; gap:8px; }
      .feedback { margin:8px 0 0; font-size:13px; padding:6px 10px; border-radius:6px; &.success{background:#dcfce7;color:#166534;} &.error{background:#fee2e2;color:#991b1b;} }
    }
  `],
})
export class AvailabilityComponent implements OnInit {
  sessions = signal<AvailabilityItem[]>([]);
  loading  = signal(true);
  pending  = signal<number | null>(null);
  feedback = signal<Record<number, { ok: boolean; msg: string } | undefined>>({});

  private weekStart = this.mondayOf(new Date());

  constructor(private booking: BookingApiService, private waitlist: WaitlistApiService) {}
  ngOnInit() { this.load(); }

  weekLabel() {
    const end = new Date(this.weekStart); end.setDate(end.getDate() + 6);
    const f = (d: Date) => d.toLocaleDateString('pt-BR', { day:'2-digit', month:'2-digit' });
    return `${f(this.weekStart)} – ${f(end)}`;
  }

  prevWeek() { this.weekStart.setDate(this.weekStart.getDate() - 7); this.load(); }
  nextWeek() { this.weekStart.setDate(this.weekStart.getDate() + 7); this.load(); }
  durationMin(s: AvailabilityItem) { return Math.round((new Date(s.endAt).getTime() - new Date(s.startAt).getTime()) / 60000); }

  book(s: AvailabilityItem) {
    this.pending.set(s.sessionId);
    this.booking.book(s.sessionId).subscribe({
      next:  () => { this.setFeedback(s.sessionId, true, '✅ Reserva confirmada!'); this.load(); },
      error: (e: { error?: { message?: string } }) => { this.pending.set(null); this.setFeedback(s.sessionId, false, e.error?.message ?? 'Erro ao reservar'); },
    });
  }

  joinWaitlist(s: AvailabilityItem) {
    this.pending.set(s.sessionId);
    this.waitlist.join(s.sessionId).subscribe({
      next:  (r) => { this.pending.set(null); this.setFeedback(s.sessionId, true, `✅ Posição ${r.position} na fila!`); },
      error: (e: { error?: { message?: string } }) => { this.pending.set(null); this.setFeedback(s.sessionId, false, e.error?.message ?? 'Erro ao entrar na fila'); },
    });
  }

  private load() {
    this.loading.set(true); this.feedback.set({});
    const from = new Date(this.weekStart).toISOString();
    const to   = new Date(this.weekStart.getTime() + 7 * 86400000).toISOString();
    this.booking.getAvailability(from, to).subscribe({
      next:  (s) => { this.sessions.set(s); this.loading.set(false); this.pending.set(null); },
      error: () => this.loading.set(false),
    });
  }

  private setFeedback(id: number, ok: boolean, msg: string) {
    this.feedback.update(f => ({ ...f, [id]: { ok, msg } }));
    setTimeout(() => this.feedback.update(f => { const n = { ...f }; delete n[id]; return n; }), 4000);
  }

  private mondayOf(d: Date): Date {
    const day = d.getDay(), diff = d.getDate() - day + (day === 0 ? -6 : 1);
    return new Date(new Date(d).setDate(diff));
  }
}

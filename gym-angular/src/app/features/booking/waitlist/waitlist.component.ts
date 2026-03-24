import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';

interface WaitlistEntry { entryId: number; sessionId: number; sessionType: string; sessionStartAt: string; position: number; status: string; notifiedAt?: string; expiresAt?: string; totalWaiting: number; }

@Component({
  selector: 'app-waitlist',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="page-header"><h1>⏳ Fila de Espera</h1></div>

    @if (loading()) {
      <div style="display:flex;justify-content:center;padding:48px"><div class="spinner"></div></div>
    } @else if (entries().length === 0) {
      <div class="empty-state">
        <div class="icon">🎉</div><h3>Nenhuma fila de espera</h3>
        <p>Você não está em nenhuma fila no momento.</p>
      </div>
    } @else {
      <div class="entries-list">
        @for (e of entries(); track e.entryId) {
          <div class="entry-card card" [class.promoted]="e.status === 'PROMOTED'">
            @if (e.status === 'PROMOTED') {
              <div class="promoted-banner">
                🎉 Vaga disponível! Confirme antes de {{ e.expiresAt | date:'dd/MM HH:mm' }}
              </div>
            }
            <div class="entry-header">
              <span class="class-name">{{ e.sessionType }}</span>
              <span class="badge" [class]="statusBadge(e.status)">
                @if (e.status === 'WAITING') { #{{ e.position }} de {{ e.totalWaiting }} }
                @else { {{ e.status }} }
              </span>
            </div>
            <p class="entry-date">{{ e.sessionStartAt | date:'EEE dd/MM HH:mm' }}</p>
            <div class="entry-actions">
              @if (e.status === 'PROMOTED') {
                <button class="btn btn-primary btn-sm" [disabled]="pending() === e.sessionId" (click)="confirm(e)">✅ Confirmar vaga</button>
              }
              <button class="btn btn-secondary btn-sm" [disabled]="pending() === e.sessionId" (click)="leave(e)">Sair da fila</button>
            </div>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .entries-list { display:flex; flex-direction:column; gap:12px; }
    .entry-card { padding:16px; &.promoted{border:2px solid var(--color-success);}
      .promoted-banner { background:#dcfce7; color:#166534; padding:8px 12px; border-radius:6px; font-size:13px; font-weight:600; margin-bottom:12px; }
      .entry-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:6px; .class-name{font-weight:600;} }
      .entry-date { font-size:13px; color:var(--color-text-muted); margin:0 0 12px; }
      .entry-actions { display:flex; gap:8px; }
    }
  `],
})
export class WaitlistComponent implements OnInit {
  entries = signal<WaitlistEntry[]>([]);
  loading = signal(true);
  pending = signal<number | null>(null);

  constructor(private http: HttpClient) {}
  ngOnInit() { this.load(); }

  confirm(e: WaitlistEntry) {
    this.pending.set(e.sessionId);
    this.http.post<unknown>(`/api/v1/waitlist/${e.sessionId}/confirm`, {}).subscribe({
      next:  () => { this.pending.set(null); this.load(); },
      error: () => this.pending.set(null),
    });
  }

  leave(e: WaitlistEntry) {
    this.pending.set(e.sessionId);
    this.http.delete(`/api/v1/waitlist/${e.sessionId}/leave`).subscribe({
      next:  () => { this.pending.set(null); this.load(); },
      error: () => this.pending.set(null),
    });
  }

  statusBadge(s: string) { return s === 'WAITING' ? 'badge badge-info' : s === 'PROMOTED' ? 'badge badge-success' : 'badge badge-neutral'; }

  private load() {
    this.loading.set(true);
    this.http.get<WaitlistEntry[]>('/api/v1/waitlist/me').subscribe({
      next:  (e) => { this.entries.set(e); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}

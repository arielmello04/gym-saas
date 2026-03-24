import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';

interface CheckinStatus { checkinId: number; status: string; provider: string; startedAt: string; completedAt?: string; }

@Component({
  selector: 'app-checkin',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="page-header"><h1>✅ Check-in</h1></div>

    <div class="providers-grid">
      @for (p of providers; track p.value) {
        <div class="provider-card card" (click)="start(p.value)">
          <span class="provider-icon">{{ p.icon }}</span>
          <span class="provider-name">{{ p.label }}</span>
          @if (pending() === p.value) {
            <div class="spinner" style="width:18px;height:18px;margin-top:8px"></div>
          }
        </div>
      }
    </div>

    @if (lastCheckin()) {
      <div class="last-checkin card">
        <h3>Último check-in</h3>
        <p>{{ lastCheckin()!.provider }} — {{ lastCheckin()!.startedAt | date:'dd/MM HH:mm' }}</p>
        <span class="badge badge-success">{{ lastCheckin()!.status }}</span>
      </div>
    }

    <h2 style="margin:28px 0 16px;font-size:18px">Histórico</h2>
    @if (history().length === 0) {
      <p style="color:var(--color-text-muted)">Nenhum check-in registrado.</p>
    } @else {
      <div class="history-list">
        @for (h of history(); track h.checkinId) {
          <div class="history-row card">
            <span>{{ h.provider }}</span>
            <span>{{ h.startedAt | date:'dd/MM/yyyy HH:mm' }}</span>
            <span class="badge badge-success">{{ h.status }}</span>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .providers-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(160px,1fr)); gap:12px; margin-bottom:28px; }
    .provider-card { display:flex; flex-direction:column; align-items:center; gap:6px; padding:24px 16px; cursor:pointer; transition:all var(--transition);
      &:hover { box-shadow:var(--shadow-md); transform:translateY(-2px); }
      .provider-icon { font-size:36px; } .provider-name { font-weight:600; font-size:14px; }
    }
    .last-checkin { margin-bottom:8px; h3{margin:0 0 8px;font-size:15px;} p{margin:0 0 8px;font-size:14px;} }
    .history-list { display:flex; flex-direction:column; gap:6px; }
    .history-row { padding:10px 14px; display:flex; justify-content:space-between; align-items:center; gap:12px; font-size:14px; }
  `],
})
export class CheckinComponent implements OnInit {
  history     = signal<CheckinStatus[]>([]);
  lastCheckin = signal<CheckinStatus | null>(null);
  pending     = signal<string | null>(null);

  readonly providers = [
    { value: 'GYMPASS',   icon: '🏋️', label: 'Gympass'   },
    { value: 'TOTALPASS', icon: '🎯', label: 'TotalPass' },
    { value: 'DIRECT',    icon: '📲', label: 'Direto'    },
  ];

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.http.get<CheckinStatus[]>('/api/v1/checkin/me').subscribe({
      next: (h: CheckinStatus[]) => { this.history.set(h); if (h.length > 0) this.lastCheckin.set(h[0]); },
    });
  }

  start(provider: string) {
    this.pending.set(provider);
    this.http.post<CheckinStatus>('/api/v1/checkin/start', { provider }).subscribe({
      next: (c: CheckinStatus) => { this.pending.set(null); this.lastCheckin.set(c); this.ngOnInit(); },
      error: (e: { error?: { message?: string } }) => { this.pending.set(null); alert(e.error?.message ?? 'Erro ao fazer check-in'); },
    });
  }
}

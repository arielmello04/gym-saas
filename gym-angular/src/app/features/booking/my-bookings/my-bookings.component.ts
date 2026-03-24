import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';

interface Booking { bookingId: number; classTypeName: string; startAt: string; status: string; }

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="page-header"><h1>🎽 Minhas Aulas</h1></div>

    @if (loading()) {
      <div style="display:flex;justify-content:center;padding:48px"><div class="spinner"></div></div>
    } @else if (bookings().length === 0) {
      <div class="empty-state">
        <div class="icon">📭</div><h3>Nenhuma reserva</h3>
        <p>Vá para a Agenda para reservar uma aula.</p>
      </div>
    } @else {
      <div class="bookings-list">
        @for (b of bookings(); track b.bookingId) {
          <div class="booking-row card">
            <div class="booking-info">
              <span class="class-name">{{ b.classTypeName }}</span>
              <span class="class-date">{{ b.startAt | date:'EEE dd/MM HH:mm' }}</span>
            </div>
            <div style="display:flex;align-items:center;gap:10px">
              <span class="badge" [class]="statusBadge(b.status)">{{ b.status }}</span>
              @if (b.status === 'BOOKED' && isFuture(b.startAt)) {
                <button class="btn btn-danger btn-sm" [disabled]="canceling() === b.bookingId" (click)="cancel(b)">
                  @if (canceling() === b.bookingId) { <span class="spinner" style="width:13px;height:13px"></span> }
                  Cancelar
                </button>
              }
            </div>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .bookings-list { display:flex; flex-direction:column; gap:10px; }
    .booking-row { padding:14px 18px; display:flex; justify-content:space-between; align-items:center; gap:12px;
      .booking-info { display:flex; flex-direction:column; gap:4px; .class-name{font-weight:600;} .class-date{font-size:13px;color:var(--color-text-muted);} }
    }
  `],
})
export class MyBookingsComponent implements OnInit {
  bookings  = signal<Booking[]>([]);
  loading   = signal(true);
  canceling = signal<number | null>(null);

  constructor(private http: HttpClient) {}
  ngOnInit() { this.load(); }

  cancel(b: Booking) {
    this.canceling.set(b.bookingId);
    this.http.delete(`/api/v1/bookings/${b.bookingId}`).subscribe({
      next:  () => { this.canceling.set(null); this.load(); },
      error: () => this.canceling.set(null),
    });
  }

  isFuture(date: string) { return new Date(date) > new Date(); }
  statusBadge(s: string) { return s === 'BOOKED' ? 'badge badge-success' : s === 'CANCELED' ? 'badge badge-danger' : 'badge badge-neutral'; }

  private load() {
    this.loading.set(true);
    this.http.get<Booking[]>('/api/v1/bookings/me').subscribe({
      next:  (b) => { this.bookings.set(b); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}

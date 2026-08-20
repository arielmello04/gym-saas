import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { BookingApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../core/services/toast.service';
import { BookingScope, MyBookingItem } from '../../../core/models';
import { IconComponent } from '../../../shared/icon.component';

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [DatePipe, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="page-title">
        <h1>Minhas aulas</h1>
        <span class="page-subtitle">Suas reservas nesta academia.</span>
      </div>
    </div>

    <div class="tabs" role="tablist">
      @for (t of tabs; track t.value) {
        <button class="tab" role="tab" [attr.aria-selected]="scope() === t.value"
                [class.active]="scope() === t.value" (click)="setScope(t.value)">
          {{ t.label }}
        </button>
      }
    </div>

    @if (loading()) {
      <div class="rows">
        @for (i of [1,2,3]; track i) { <div class="skeleton" style="height:64px"></div> }
      </div>
    } @else if (bookings().length === 0) {
      <div class="empty-state">
        <div class="icon"><app-icon name="ticket" [size]="40" [stroke]="1.4" /></div>
        <h3>{{ emptyTitle() }}</h3>
        <p>Reserve uma aula pela agenda para ela aparecer aqui.</p>
        <a routerLink="/dashboard" class="btn btn-primary">Ver agenda</a>
      </div>
    } @else {
      <div class="rows">
        @for (b of bookings(); track b.bookingId) {
          <div class="row enter">
            <div class="row-main">
              <span class="row-title">{{ b.classTypeName }}</span>
              <span class="row-sub nums">{{ b.startAt | date:'EEEE, dd/MM' }} · {{ b.startAt | date:'HH:mm' }}–{{ b.endAt | date:'HH:mm' }}</span>
            </div>
            <div class="row-side">
              <span class="badge" [class]="badgeClass(b.status)">{{ statusLabel(b.status) }}</span>
              @if (b.status === 'BOOKED' && b.cancellable) {
                <button class="btn btn-danger btn-sm" [disabled]="canceling() === b.bookingId" (click)="cancel(b)">
                  @if (canceling() === b.bookingId) { <span class="spinner spinner-sm"></span> }
                  Cancelar
                </button>
              } @else if (b.status === 'BOOKED') {
                <span class="dim locked">Fora do prazo</span>
              }
            </div>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .tabs { display: inline-flex; gap: 4px; padding: 4px; margin-bottom: 20px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--r); }
    .tab { padding: 7px 16px; border: none; border-radius: var(--r-sm); background: transparent; color: var(--text-muted); font-family: var(--font); font-size: 13px; font-weight: 600; cursor: pointer; transition: background var(--t-fast), color var(--t-fast); }
    .tab:hover { color: var(--text); }
    .tab.active { background: var(--accent); color: var(--accent-ink); }
    .row-sub { text-transform: capitalize; }
    .locked { font-size: 12px; }
  `],
})
export class MyBookingsComponent implements OnInit {
  private api = inject(BookingApiService);
  private toast = inject(ToastService);

  readonly tabs: { value: BookingScope; label: string }[] = [
    { value: 'upcoming', label: 'Próximas' },
    { value: 'past',     label: 'Anteriores' },
    { value: 'all',      label: 'Todas' },
  ];

  bookings = signal<MyBookingItem[]>([]);
  loading = signal(true);
  canceling = signal<number | null>(null);
  scope = signal<BookingScope>('upcoming');

  ngOnInit() { this.load(); }

  setScope(s: BookingScope) {
    if (this.scope() === s) return;
    this.scope.set(s);
    this.load();
  }

  emptyTitle() {
    return this.scope() === 'past' ? 'Nenhuma aula anterior' : 'Nenhuma reserva';
  }

  statusLabel(s: string) {
    return s === 'BOOKED' ? 'Confirmada' : s === 'CANCELED' ? 'Cancelada' : s;
  }

  badgeClass(s: string) {
    return s === 'BOOKED' ? 'badge-success' : s === 'CANCELED' ? 'badge-neutral' : 'badge-info';
  }

  cancel(b: MyBookingItem) {
    this.canceling.set(b.bookingId);
    this.api.cancel(b.bookingId).subscribe({
      next: () => {
        this.canceling.set(null);
        this.toast.success('Reserva cancelada. A vaga foi liberada para a fila.');
        this.load();
      },
      error: e => {
        this.canceling.set(null);
        this.toast.fromApi(e, 'Não foi possível cancelar a reserva.');
      },
    });
  }

  private load() {
    this.loading.set(true);
    this.api.myBookings(this.scope()).subscribe({
      next: b => { this.bookings.set(b); this.loading.set(false); },
      error: e => {
        this.loading.set(false);
        this.toast.fromApi(e, 'Não foi possível carregar suas reservas.');
      },
    });
  }
}

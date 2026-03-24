import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface Member { userId: number; email: string; role: string; active: boolean; }

@Component({
  selector: 'app-members',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="page-header"><h1>👥 Membros</h1></div>

    <div class="card add-form">
      <h3>Adicionar membro</h3>
      <div style="display:flex;gap:10px;flex-wrap:wrap">
        <div class="form-group" style="flex:1;min-width:200px;margin:0">
          <input [(ngModel)]="newEmail" placeholder="email@exemplo.com" type="email">
        </div>
        <div class="form-group" style="margin:0">
          <select [(ngModel)]="newRole">
            @for (r of roles; track r) { <option>{{ r }}</option> }
          </select>
        </div>
        <button class="btn btn-primary" [disabled]="adding()" (click)="add()">
          @if (adding()) { <span class="spinner" style="width:14px;height:14px"></span> }
          Adicionar
        </button>
      </div>
      @if (addError()) { <p style="color:var(--color-danger);font-size:13px;margin:8px 0 0">{{ addError() }}</p> }
    </div>

    @if (loading()) {
      <div style="display:flex;justify-content:center;padding:48px"><div class="spinner"></div></div>
    } @else {
      <div class="members-list">
        @for (m of members(); track m.userId) {
          <div class="member-row card">
            <div style="display:flex;align-items:center;gap:10px">
              <span style="font-size:14px">{{ m.email }}</span>
              <span class="badge badge-info">{{ m.role }}</span>
            </div>
            <button class="btn btn-danger btn-sm" [disabled]="removing() === m.userId" (click)="remove(m)">
              @if (removing() === m.userId) { <span class="spinner" style="width:13px;height:13px"></span> }
              Remover
            </button>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .add-form { margin-bottom:20px; h3{margin:0 0 14px;font-size:15px;} }
    .members-list { display:flex; flex-direction:column; gap:8px; }
    .member-row { padding:12px 16px; display:flex; justify-content:space-between; align-items:center; }
  `],
})
export class MembersComponent implements OnInit {
  members  = signal<Member[]>([]);
  loading  = signal(true);
  adding   = signal(false);
  removing = signal<number | null>(null);
  addError = signal('');
  newEmail = ''; newRole = 'MEMBER';
  readonly roles = ['MEMBER','TRAINER','STAFF','MANAGER'];

  constructor(private http: HttpClient) {}
  ngOnInit() { this.load(); }

  add() {
    if (!this.newEmail) return;
    this.adding.set(true); this.addError.set('');
    this.http.post<Member>('/api/v1/tenant/members', { email: this.newEmail, role: this.newRole }).subscribe({
      next:  () => { this.adding.set(false); this.newEmail = ''; this.load(); },
      error: (e: { error?: { message?: string } }) => { this.adding.set(false); this.addError.set(e.error?.message ?? 'Erro ao adicionar'); },
    });
  }

  remove(m: Member) {
    if (!confirm(`Remover ${m.email}?`)) return;
    this.removing.set(m.userId);
    this.http.delete(`/api/v1/tenant/members/${m.userId}`).subscribe({
      next:  () => { this.removing.set(null); this.load(); },
      error: () => this.removing.set(null),
    });
  }

  private load() {
    this.loading.set(true);
    this.http.get<Member[]>('/api/v1/tenant/members').subscribe({
      next:  (m) => { this.members.set(m); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}

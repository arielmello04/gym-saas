import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProfileApiService } from '../../core/services/api.service';
import { ProfilePreferences } from '../../core/models';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="page-header"><h1>👤 Perfil & Preferências</h1></div>

    @if (loading()) {
      <div style="display:flex;justify-content:center;padding:48px"><div class="spinner"></div></div>
    } @else {
      <div class="card" style="max-width:480px">
        <h3 style="margin:0 0 20px">Privacidade</h3>

        <label class="toggle-row">
          <span>Permitir gravações</span>
          <input type="checkbox" [(ngModel)]="prefs!.allowRecording">
        </label>
        <label class="toggle-row">
          <span>Permitir fotos</span>
          <input type="checkbox" [(ngModel)]="prefs!.allowPhotos">
        </label>
        <label class="toggle-row">
          <span>Permitir visibilidade do rosto</span>
          <input type="checkbox" [(ngModel)]="prefs!.allowFaceVisibility">
        </label>

        @if (saved()) {
          <p class="saved-msg">✅ Preferências salvas!</p>
        }

        <button class="btn btn-primary" style="margin-top:20px" [disabled]="saving()" (click)="save()">
          @if (saving()) { <span class="spinner" style="width:14px;height:14px"></span> }
          Salvar
        </button>
      </div>
    }
  `,
  styles: [`
    .toggle-row { display:flex; justify-content:space-between; align-items:center; padding:12px 0; border-bottom:1px solid var(--color-border); cursor:pointer; font-size:14px;
      input[type=checkbox] { width:18px; height:18px; cursor:pointer; }
      &:last-of-type { border-bottom:none; }
    }
    .saved-msg { color:var(--color-success); font-size:14px; margin:12px 0 0; }
  `],
})
export class ProfileComponent implements OnInit {
  prefs:   ProfilePreferences | null = null;
  loading  = signal(true);
  saving   = signal(false);
  saved    = signal(false);

  constructor(private api: ProfileApiService) {}

  ngOnInit() {
    this.api.get().subscribe({
      next:  (p) => { this.prefs = { ...p }; this.loading.set(false); },
      error: () => { this.prefs = { allowRecording: true, allowPhotos: true, allowFaceVisibility: true }; this.loading.set(false); },
    });
  }

  save() {
    if (!this.prefs) return;
    this.saving.set(true);
    this.api.update(this.prefs).subscribe({
      next:  () => { this.saving.set(false); this.saved.set(true); setTimeout(() => this.saved.set(false), 3000); },
      error: () => this.saving.set(false),
    });
  }
}

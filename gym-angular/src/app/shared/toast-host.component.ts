import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from '../core/services/toast.service';

@Component({
  selector: 'app-toast-host',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toast-host" role="status" aria-live="polite">
      @for (t of toast.toasts(); track t.id) {
        <div class="toast" [class]="'toast-' + t.kind" (click)="toast.dismiss(t.id)">
          <span class="toast-mark"></span>
          <span class="toast-text">{{ t.text }}</span>
        </div>
      }
    </div>
  `,
  styles: [`
    .toast-host {
      position: fixed; z-index: 300;
      right: 20px; bottom: 20px;
      display: flex; flex-direction: column; gap: 10px;
      max-width: min(380px, calc(100vw - 40px));
      pointer-events: none;
    }
    .toast {
      display: flex; align-items: flex-start; gap: 10px;
      padding: 13px 15px;
      background: var(--surface-hi);
      border: 1px solid var(--border-strong);
      border-radius: var(--r);
      box-shadow: var(--shadow-md);
      font-size: 14px;
      cursor: pointer;
      pointer-events: auto;
      animation: rise var(--t-slow) both;
    }
    .toast-mark { width: 3px; align-self: stretch; border-radius: 2px; flex: none; }
    .toast-text { min-width: 0; }
    .toast-success .toast-mark { background: var(--success); }
    .toast-error   .toast-mark { background: var(--danger); }
    .toast-info    .toast-mark { background: var(--info); }
    @media (max-width: 900px) { .toast-host { left: 16px; right: 16px; bottom: 16px; max-width: none; } }
  `],
})
export class ToastHostComponent {
  toast = inject(ToastService);
}

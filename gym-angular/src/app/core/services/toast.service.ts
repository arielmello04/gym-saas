import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  text: string;
}

/**
 * Avisos curtos no canto da tela.
 *
 * Substitui o alert() nativo, que bloqueia a aba inteira, ignora o tema e
 * some sem deixar rastro do que aconteceu.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private seq = 0;
  readonly toasts = signal<Toast[]>([]);

  success(text: string) { this.push('success', text); }
  error(text: string)   { this.push('error', text); }
  info(text: string)    { this.push('info', text); }

  /** Mensagem de erro da API, com uma frase de reserva quando não vier nenhuma. */
  fromApi(e: unknown, fallback: string) {
    const msg = (e as { error?: { message?: string } })?.error?.message;
    this.error(msg?.trim() || fallback);
  }

  dismiss(id: number) {
    this.toasts.update(list => list.filter(t => t.id !== id));
  }

  private push(kind: ToastKind, text: string) {
    const id = ++this.seq;
    this.toasts.update(list => [...list, { id, kind, text }]);
    // Erro fica mais tempo: costuma trazer instrução do que fazer a seguir.
    setTimeout(() => this.dismiss(id), kind === 'error' ? 6000 : 3800);
  }
}

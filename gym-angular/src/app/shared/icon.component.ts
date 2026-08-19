import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type IconName =
  | 'calendar' | 'ticket' | 'clock' | 'card' | 'receipt' | 'check'
  | 'user' | 'users' | 'money' | 'tag' | 'logout' | 'menu' | 'dumbbell'
  | 'arrow-left' | 'arrow-right' | 'plus' | 'x';

/**
 * Ícones de traço, no lugar dos emoji.
 *
 * Emoji renderiza diferente em cada sistema, não acompanha a cor do texto e
 * quebra o alinhamento vertical — três coisas que aparecem justamente na
 * navegação, onde os ícones ficam lado a lado.
 *
 * O SVG está escrito literalmente no template em vez de vir por innerHTML:
 * assim herda `currentColor` e não passa pelo sanitizador, que remove parte
 * do conteúdo SVG injetado.
 */
@Component({
  selector: 'app-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg [attr.width]="size" [attr.height]="size" viewBox="0 0 24 24" fill="none"
         stroke="currentColor" [attr.stroke-width]="stroke"
         stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      @switch (name) {
        @case ('calendar') {
          <rect x="3" y="5" width="18" height="16" rx="3" />
          <path d="M3 10h18M8 3v4M16 3v4" />
        }
        @case ('ticket') {
          <path d="M4 8a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-4z" />
          <path d="M14 6v12" />
        }
        @case ('clock') {
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v5l3 2" />
        }
        @case ('card') {
          <rect x="2" y="5" width="20" height="14" rx="3" />
          <path d="M2 10h20" />
        }
        @case ('receipt') {
          <path d="M6 3h12v18l-3-2-3 2-3-2-3 2z" />
          <path d="M9 8h6M9 12h6" />
        }
        @case ('check') {
          <circle cx="12" cy="12" r="9" />
          <path d="m8.5 12 2.5 2.5 4.5-5" />
        }
        @case ('user') {
          <circle cx="12" cy="8" r="4" />
          <path d="M4 21c0-4 3.6-6 8-6s8 2 8 6" />
        }
        @case ('users') {
          <circle cx="9" cy="8" r="3.5" />
          <path d="M2 20c0-3.5 3.2-5.5 7-5.5s7 2 7 5.5" />
          <path d="M17 5.2a3.5 3.5 0 0 1 0 6.6M18.5 14.4c2.1.7 3.5 2.2 3.5 4.6" />
        }
        @case ('money') {
          <rect x="2" y="6" width="20" height="12" rx="3" />
          <circle cx="12" cy="12" r="2.5" />
          <path d="M6 12h.01M18 12h.01" />
        }
        @case ('tag') {
          <path d="M3 12V5a2 2 0 0 1 2-2h7l9 9-9 9z" />
          <circle cx="7.5" cy="7.5" r="1.5" />
        }
        @case ('logout') {
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
          <path d="m16 17 5-5-5-5M21 12H9" />
        }
        @case ('menu') {
          <path d="M4 6h16M4 12h16M4 18h16" />
        }
        @case ('dumbbell') {
          <path d="M6.5 6.5v11M3 9v6M17.5 6.5v11M21 9v6M6.5 12h11" />
        }
        @case ('arrow-left')  { <path d="m14 6-6 6 6 6" /> }
        @case ('arrow-right') { <path d="m10 6 6 6-6 6" /> }
        @case ('plus')        { <path d="M12 5v14M5 12h14" /> }
        @case ('x')           { <path d="M6 6l12 12M18 6L6 18" /> }
      }
    </svg>
  `,
  styles: [`:host { display: inline-flex; flex: none; }`],
})
export class IconComponent {
  @Input({ required: true }) name!: IconName;
  @Input() size = 18;
  @Input() stroke = 1.75;
}

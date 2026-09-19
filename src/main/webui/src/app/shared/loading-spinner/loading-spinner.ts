import { Component, input } from '@angular/core';
import { Logo } from '../logo/logo';

/**
 * The app's loading indicator: the piggy mark spinning in place. Exists because the Lambda
 * backend cold-starts (see CLAUDE.md, "Why EventBridge Scheduler...") - a first request can
 * take a few real seconds, and every page that fetches data on load used to either show
 * nothing at all (a blank page under the header) or a small, easy-to-miss "Ładowanie..."
 * line, both of which read as "this is broken/empty" rather than "this is loading" to
 * someone who doesn't know Lambda cold starts are a thing. One shared, visible spinner
 * everywhere instead of each page inventing (or forgetting) its own.
 */
@Component({
  selector: 'app-loading-spinner',
  imports: [Logo],
  template: `
    <div class="spinner-wrap" [class.spinner-wrap--inline]="inline()">
      <app-logo [size]="size()" class="spinner-mark" />
      @if (label(); as l) {
        <p class="spinner-label">{{ l }}</p>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .spinner-wrap {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.75rem;
      padding: 3rem 0;
      color: var(--gg-ink-soft);
    }
    .spinner-wrap--inline {
      flex-direction: row;
      padding: 0;
    }
    .spinner-mark {
      color: var(--gg-coin-deep);
      animation: gg-spin 1.1s cubic-bezier(0.65, 0, 0.35, 1) infinite;
    }
    .spinner-label {
      margin: 0;
      font-size: 0.9rem;
    }
    @keyframes gg-spin {
      to {
        transform: rotate(360deg);
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .spinner-mark {
        animation: none;
      }
    }
  `,
})
export class LoadingSpinner {
  readonly size = input(44);
  /** Text shown under the mark - pass a translated string, e.g. `[label]="'common.loading' | translate"`. */
  readonly label = input<string | null>(null);
  /** Small side-by-side layout for use next to other content instead of a full-page block. */
  readonly inline = input(false);
}

import { Component, input } from '@angular/core';

/**
 * The app's mark: a piggy bank with a coin dropping into the slot ("grosz do grosza" -
 * penny by penny). Body/details use currentColor (with opacity for shading) so the same
 * markup reads correctly both on the dark toolbar (cream mark) and on light hero surfaces
 * (ink or gold mark); the coin itself stays a fixed gold so it always pops regardless of
 * context, mirroring the app's real coin accent color.
 */
@Component({
  selector: 'app-logo',
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 64 64" fill="none" aria-hidden="true">
      <path
        d="M8 32q-2-1-2-7t4-6q3-8 20-8 14 0 19 8h3a4 4 0 0 1 4 4v3a4 4 0 0 1-4 4h-2l-2 5v8a3 3 0 0 1-3 3h-4a3 3 0 0 1-3-3v-2H22v2a3 3 0 0 1-3 3h-4a3 3 0 0 1-3-3v-5q-5-2-7-6z"
        fill="currentColor"
      />
      <circle cx="42" cy="26" r="2.4" fill="currentColor" opacity="0.55" />
      <path d="M9 30q-3-2-2-7" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" opacity="0.55" fill="none" />
      <rect x="27" y="12" width="12" height="4" rx="2" fill="currentColor" opacity="0.35" />
      <circle cx="33" cy="6" r="5.5" fill="#e0b64f" stroke="#c99524" stroke-width="1" />
      <path d="M33 3.2v5.6M30.6 6h4.8" stroke="#c99524" stroke-width="0.8" stroke-linecap="round" />
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
    }
  `,
})
export class Logo {
  readonly size = input(28);
}

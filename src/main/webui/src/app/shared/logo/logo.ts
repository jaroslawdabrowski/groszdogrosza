import { Component, input } from '@angular/core';

/**
 * The app's mark: a piggy bank with a coin dropping toward the slot ("grosz do grosza" -
 * penny by penny). The pig silhouette is Google's Material Symbols "savings" glyph
 * (Apache 2.0, github.com/google/material-design-icons) - hand-drawn attempts at a piggy
 * bank silhouette in earlier iterations of this component didn't actually read as a pig
 * (wrong snout/leg proportions), so this reuses a professionally-drawn one instead and adds
 * only the coin, matching the "download and adapt, don't redraw from scratch" call. Body
 * uses currentColor so the same markup reads correctly both on the dark toolbar (cream
 * mark) and on light hero surfaces (ink mark); the eye and coin slot are cut out of the
 * silhouette itself (compound path), so they naturally pick up whatever's behind the mark
 * instead of needing a second color. The coin stays a fixed gold so it always pops
 * regardless of context, mirroring the app's real coin accent color.
 */
@Component({
  selector: 'app-logo',
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 -960 960 960" fill="none" aria-hidden="true">
      <path
        d="M230-120q-21 0-39.49-13.96Q172.02-147.93 166-168q-25-86-41.54-148.46-16.54-62.46-26.37-109.68-9.82-47.22-13.95-83.79Q80-546.49 80-580q0-92 64-156t156-64h200q27-36 68.5-58t91.5-22q25 0 42.5 17.5T720-820q0 6-1.5 12t-3.5 11q-4 11-7.5 22t-5.5 24l91 91h57q12.75 0 21.38 8.62Q880-642.75 880-630v227q0 10.24-5.5 18.12Q869-377 859-374l-91.93 30.3L713-163q-6.03 19.61-21.84 31.31Q675.34-120 655-120H540q-24.75 0-42.37-17.63Q480-155.25 480-180v-20h-80v20q0 24.75-17.62 42.37Q364.75-120 340-120H230Zm410-400q17 0 28.5-11.5T680-560q0-17-11.5-28.5T640-600q-17 0-28.5 11.5T600-560q0 17 11.5 28.5T640-520ZM490-620q12.75 0 21.38-8.68 8.62-8.67 8.62-21.5 0-12.82-8.62-21.32-8.63-8.5-21.38-8.5H350q-12.75 0-21.37 8.68-8.63 8.67-8.63 21.5 0 12.82 8.63 21.32 8.62 8.5 21.37 8.5h140Z"
        fill="currentColor"
      />
      <circle cx="300" cy="-790" r="55" fill="#e8b74a" stroke="#c6942a" stroke-width="12" />
      <path d="M300 -822v64M268 -790h64" stroke="#c6942a" stroke-width="11" stroke-linecap="round" />
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

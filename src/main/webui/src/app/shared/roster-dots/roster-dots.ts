import { Component, computed, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

type DotState = 'paid' | 'partial' | 'none' | 'out';

/**
 * The class roster as a row of dots, one per student: filled = paid, half = paid part of it,
 * empty = nothing yet, dashed = not taking part. It answers the treasurer's real question
 * ("who's still missing?") at a glance and doubles as the count.
 *
 * The dots fill in one after another the first time they render - the app's one orchestrated
 * motion (docs/DESIGN.md, "Motion"). Dots that already exist don't re-animate when the counts
 * change, only newly created ones do.
 */
@Component({
  selector: 'app-roster-dots',
  imports: [TranslatePipe],
  template: `
    <div
      class="roster-dots roster-dots--animate"
      role="img"
      [attr.aria-label]="'roster.label' | translate: { paid: paid(), total: total() }"
      data-testid="roster-dots"
    >
      @for (state of dots(); track $index) {
        <span class="roster-dot" [class]="'roster-dot roster-dot--' + state" [style.--i]="$index"></span>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
})
export class RosterDots {
  readonly paid = input(0);
  readonly partial = input(0);
  readonly total = input(0);
  /** Students in the class but not in this collection (shown dashed, after everyone else). */
  readonly out = input(0);

  readonly dots = computed<DotState[]>(() => {
    const paid = Math.max(0, this.paid());
    const partial = Math.max(0, this.partial());
    const none = Math.max(0, this.total() - paid - partial);
    return [
      ...Array<DotState>(paid).fill('paid'),
      ...Array<DotState>(partial).fill('partial'),
      ...Array<DotState>(none).fill('none'),
      ...Array<DotState>(Math.max(0, this.out())).fill('out'),
    ];
  });
}

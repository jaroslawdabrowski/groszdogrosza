import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';
import { MyStudentStatus } from '../../core/models';

/**
 * A small icon on a collection card/widget answering "does MY child take part in this
 * collection, and have they paid" - the one piece of per-student detail a regular parent is
 * allowed to see here (see backend CollectionResponse.myStudentStatus's javadoc: never
 * another family's). Used on Dashboard's cards, PublicOverview's cards, and a non-treasurer
 * parent's own CollectionDetails progress view - all three read the exact same
 * `collection.myStudentStatus` field, computed once on the backend.
 *
 * Renders nothing when `status` is null/undefined - either nobody's logged in, or the
 * account has no Parent record linked to a Student yet, so there's no "own child" to show
 * anything about.
 */
@Component({
  selector: 'app-my-student-status-badge',
  imports: [MatIconModule, MatTooltipModule, TranslatePipe],
  template: `
    @if (status(); as s) {
      <span class="my-student-status my-student-status--{{ s }}" [matTooltip]="('myStudentStatus.' + s) | translate">
        <mat-icon>{{ iconFor(s) }}</mat-icon>
      </span>
    }
  `,
  styles: `
    .my-student-status {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      line-height: 0;
    }
    .my-student-status mat-icon {
      font-size: 22px;
      width: 22px;
      height: 22px;
    }
    .my-student-status--PAID mat-icon,
    .my-student-status--OVERPAID mat-icon {
      color: var(--gg-mint);
    }
    .my-student-status--PENDING mat-icon {
      color: var(--gg-blush);
    }
    .my-student-status--NOT_INCLUDED mat-icon {
      color: var(--gg-ink-soft);
      opacity: 0.7;
    }
  `,
})
export class MyStudentStatusBadge {
  readonly status = input<MyStudentStatus | null | undefined>(null);

  iconFor(status: MyStudentStatus): string {
    if (status === 'PAID' || status === 'OVERPAID') {
      return 'check_circle';
    }
    if (status === 'PENDING') {
      return 'cancel';
    }
    return 'do_not_disturb_on';
  }
}

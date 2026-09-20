import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { CollectionApiService } from '../core/collection-api.service';
import {
  CollectionDetails as CollectionDetailsModel,
  CollectionProgress,
  SettlementResult,
  isCollectionDetails,
} from '../core/models';
import { LoadingSpinner } from '../shared/loading-spinner/loading-spinner';

/**
 * Renders one of two shapes depending on the caller's role, as returned by the backend
 * (see CollectionResource.get / CollectionProgressResponse): a treasurer sees the full
 * per-student requirement/contribution breakdown and the settle form, a regular parent sees
 * aggregate progress only.
 */
@Component({
  selector: 'app-collection-details',
  imports: [
    RouterLink,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatChipsModule,
    MatTooltipModule,
    MatProgressBarModule,
    TranslatePipe,
    LoadingSpinner,
  ],
  templateUrl: './collection-details.html',
  styleUrl: './collection-details.scss',
})
export class CollectionDetails {
  private readonly route = inject(ActivatedRoute);
  private readonly collectionApi = inject(CollectionApiService);
  private readonly translate = inject(TranslateService);

  readonly view = signal<CollectionDetailsModel | CollectionProgress | null>(null);
  /** Only guards the FIRST load (a Lambda cold start can take real seconds - see
   *  LoadingSpinner's javadoc) - never set back to true, so a settle()/removeStudent()
   *  triggered reload() doesn't flash the whole page back to a spinner; `view()` already
   *  holds the previous value while that reload is in flight. */
  readonly loading = signal(true);
  readonly settlementPreview = signal<SettlementResult | null>(null);
  readonly actualCostSpent = signal<number>(0);
  readonly requirementColumns = ['studentName', 'requiredAmount', 'paidAmount', 'status'];
  readonly isCollectionDetails = isCollectionDetails;

  private readonly collectionId: string;

  constructor() {
    this.collectionId = this.route.snapshot.paramMap.get('id')!;
    this.reload();
  }

  reload(): void {
    this.collectionApi.get(this.collectionId).subscribe({
      next: (view) => {
        this.view.set(view);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  settle(): void {
    this.collectionApi.settle(this.collectionId, this.actualCostSpent()).subscribe((result) => {
      this.settlementPreview.set(result);
      this.reload();
    });
  }

  /** Only meaningful while the collection is ACTIVE - see backend
   *  RemoveStudentFromCollectionUseCase, which refunds whatever the student already paid
   *  back to their piggy bank. Adds the 'actions' column to the requirements table (see
   *  visibleRequirementColumns) only in that state. */
  removeStudent(studentId: string, studentName: string): void {
    const confirmed = window.confirm(this.translate.instant('collectionDetails.removeStudentConfirm', { name: studentName }));
    if (!confirmed) {
      return;
    }
    this.collectionApi.removeStudent(this.collectionId, studentId).subscribe(() => this.reload());
  }

  visibleRequirementColumns(): string[] {
    const current = this.view();
    if (current && isCollectionDetails(current) && current.collection.status === 'ACTIVE') {
      return [...this.requirementColumns, 'actions'];
    }
    return this.requirementColumns;
  }

  /** SettlementResult only carries studentId (see backend SettlementResultResponse) - name
   *  it against whatever the last-loaded requirements breakdown knows, so the settlement
   *  card can show a name instead of a raw id. */
  nameForStudent(studentId: string): string {
    const current = this.view();
    if (current && isCollectionDetails(current)) {
      const match = current.requirements.find((r) => r.studentId === studentId);
      if (match) {
        return match.studentName;
      }
    }
    return studentId;
  }

  /** Money actually received for this collection - the sum of every recorded Contribution,
   *  not the requirements' paidAmount (which a settled collection can leave at a value that
   *  no longer matches what's actually in hand after leftovers are swept back out). This is
   *  the number a treasurer would want on a report handed over with cash/a transfer receipt
   *  - see the print report below. */
  totalCollected(): number {
    const current = this.view();
    if (!current || !isCollectionDetails(current)) {
      return 0;
    }
    return current.contributions.reduce((sum, c) => sum + c.amount, 0);
  }

  /** Only meaningful the moment it's called (not stored as state) - the print report always
   *  shows "printed on <today>", so it's fine to compute fresh on each render rather than
   *  fixing it once. Formatted in the app's currently-chosen language, not the browser's own
   *  locale - the rest of the printed page (labels, statuses) is already in that language,
   *  and this report is meant to be handed to someone else (a teacher), so it should read
   *  consistently regardless of what locale the treasurer's own browser happens to be set to. */
  printedOnLabel(): string {
    const locale = this.translate.currentLang() === 'en' ? 'en-US' : 'pl-PL';
    return new Date().toLocaleDateString(locale, { year: 'numeric', month: 'long', day: 'numeric' });
  }

  print(): void {
    window.print();
  }
}

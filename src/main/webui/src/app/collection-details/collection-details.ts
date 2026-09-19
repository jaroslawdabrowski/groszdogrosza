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
  ],
  templateUrl: './collection-details.html',
  styleUrl: './collection-details.scss',
})
export class CollectionDetails {
  private readonly route = inject(ActivatedRoute);
  private readonly collectionApi = inject(CollectionApiService);
  private readonly translate = inject(TranslateService);

  readonly view = signal<CollectionDetailsModel | CollectionProgress | null>(null);
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
    this.collectionApi.get(this.collectionId).subscribe((view) => this.view.set(view));
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
}

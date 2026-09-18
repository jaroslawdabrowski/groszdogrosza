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
import { TranslatePipe } from '@ngx-translate/core';
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
 * per-parent requirement/contribution breakdown and the settle form, a regular parent sees
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
    MatProgressBarModule,
    TranslatePipe,
  ],
  templateUrl: './collection-details.html',
  styleUrl: './collection-details.scss',
})
export class CollectionDetails {
  private readonly route = inject(ActivatedRoute);
  private readonly collectionApi = inject(CollectionApiService);

  readonly view = signal<CollectionDetailsModel | CollectionProgress | null>(null);
  readonly settlementPreview = signal<SettlementResult | null>(null);
  readonly actualCostSpent = signal<number>(0);
  readonly requirementColumns = ['parentId', 'requiredAmount', 'paidAmount', 'status'];
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
}

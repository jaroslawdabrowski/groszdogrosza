import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { TranslatePipe } from '@ngx-translate/core';
import { CollectionApiService } from '../core/collection-api.service';
import { CollectionSummary } from '../core/models';
import { LoadingSpinner } from '../shared/loading-spinner/loading-spinner';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, MatCardModule, MatChipsModule, MatProgressBarModule, TranslatePipe, LoadingSpinner],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly collectionApi = inject(CollectionApiService);

  readonly collections = signal<CollectionSummary[]>([]);
  readonly loading = signal(true);

  constructor() {
    this.collectionApi.list().subscribe({
      next: (collections) => {
        this.collections.set(collections);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  cardClassFor(status: string): string {
    if (status === 'SETTLED') {
      return 'gg-card gg-card--mint';
    }
    if (status === 'DRAFT') {
      return 'gg-card gg-card--sky';
    }
    return 'gg-card gg-card--blush';
  }
}

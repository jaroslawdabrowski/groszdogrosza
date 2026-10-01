import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';
import { CollectionApiService } from '../core/collection-api.service';
import { CurrentUserService } from '../core/current-user.service';
import { CollectionSummary } from '../core/models';
import { LoadingSpinner } from '../shared/loading-spinner/loading-spinner';
import { MoneyPipe } from '../shared/money.pipe';

/** "Zbiórki": every collection, active ones first. Each row says where the viewer's own child
 *  stands (owes / paid / not taking part) when there is one, otherwise the collection's state. */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, MatIconModule, TranslatePipe, LoadingSpinner, MoneyPipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly collectionApi = inject(CollectionApiService);
  private readonly currentUser = inject(CurrentUserService);

  readonly collections = signal<CollectionSummary[]>([]);
  readonly loading = signal(true);

  readonly active = computed(() => this.sorted(this.collections().filter((c) => c.status === 'ACTIVE')));
  readonly finished = computed(() => this.sorted(this.collections().filter((c) => c.status !== 'ACTIVE')));

  constructor() {
    this.collectionApi.list().subscribe({
      next: (collections) => {
        this.collections.set(collections);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.currentUser.load().subscribe();
  }

  isTreasurer(): boolean {
    return this.currentUser.isTreasurer();
  }

  /** Pill for the row: the viewer's own child's status when known, else the collection's. */
  pill(c: CollectionSummary): { key: string; tone: string } {
    switch (c.myStudentStatus) {
      case 'PENDING':
        return { key: 'myStudentStatus.short.PENDING', tone: 'pill--owe' };
      case 'PAID':
      case 'OVERPAID':
        return { key: 'myStudentStatus.short.PAID', tone: 'pill--ok' };
      case 'NOT_INCLUDED':
        return { key: 'myStudentStatus.short.NOT_INCLUDED', tone: '' };
      default:
        return { key: `collection.status.${c.status}`, tone: c.status === 'ACTIVE' ? 'pill--blue' : '' };
    }
  }

  private sorted(list: CollectionSummary[]): CollectionSummary[] {
    return [...list].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }
}

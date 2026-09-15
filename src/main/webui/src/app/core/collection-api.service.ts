import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CollectionDetails, CollectionProgress, CollectionSummary, SettlementResult } from './models';

@Injectable({ providedIn: 'root' })
export class CollectionApiService {
  private readonly http = inject(HttpClient);

  list(): Observable<CollectionSummary[]> {
    return this.http.get<CollectionSummary[]>('/api/collections');
  }

  /** Returns CollectionDetails (treasurer) or CollectionProgress (regular parent) depending
   * on the caller's role - see the backend's CollectionResource.get. Use `isCollectionDetails`
   * from ./models to narrow the result. */
  get(id: string): Observable<CollectionDetails | CollectionProgress> {
    return this.http.get<CollectionDetails | CollectionProgress>(`/api/collections/${id}`);
  }

  create(title: string, description: string, baseAmountPerParent: number): Observable<CollectionSummary> {
    return this.http.post<CollectionSummary>('/api/collections', { title, description, baseAmountPerParent });
  }

  recordContribution(collectionId: string, parentId: string, amount: number): Observable<CollectionDetails> {
    return this.http.post<CollectionDetails>(`/api/collections/${collectionId}/contributions`, { parentId, amount });
  }

  settle(collectionId: string, actualCostSpent: number): Observable<SettlementResult> {
    return this.http.post<SettlementResult>(`/api/collections/${collectionId}/settle`, { actualCostSpent });
  }
}

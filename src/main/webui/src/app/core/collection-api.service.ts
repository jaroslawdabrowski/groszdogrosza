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

  /** `studentIds` are the students this collection asks money from - not every collection
   *  includes every student (a trip some families already opted their kid out of) - see the
   *  backend CreateCollectionUseCase's javadoc. */
  create(title: string, description: string, baseAmountPerStudent: number, studentIds: string[]): Observable<CollectionSummary> {
    return this.http.post<CollectionSummary>('/api/collections', { title, description, baseAmountPerStudent, studentIds });
  }

  recordContribution(collectionId: string, studentId: string, amount: number): Observable<CollectionDetails> {
    return this.http.post<CollectionDetails>(`/api/collections/${collectionId}/contributions`, { studentId, amount });
  }

  settle(collectionId: string, actualCostSpent: number): Observable<SettlementResult> {
    return this.http.post<SettlementResult>(`/api/collections/${collectionId}/settle`, { actualCostSpent });
  }

  /** Takes a student out of an ACTIVE collection - whatever they'd already paid is refunded
   *  to their piggy bank (see the backend RemoveStudentFromCollectionUseCase). */
  removeStudent(collectionId: string, studentId: string): Observable<CollectionDetails> {
    return this.http.delete<CollectionDetails>(`/api/collections/${collectionId}/students/${studentId}`);
  }
}

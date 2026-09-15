import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { LedgerEntry } from './models';

@Injectable({ providedIn: 'root' })
export class LedgerApiService {
  private readonly http = inject(HttpClient);

  getFor(parentId: string): Observable<LedgerEntry[]> {
    return this.http.get<LedgerEntry[]>(`/api/parents/${parentId}/ledger`);
  }
}

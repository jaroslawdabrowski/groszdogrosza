import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { GlobalLedgerEntry, LedgerEntry } from './models';

@Injectable({ providedIn: 'root' })
export class LedgerApiService {
  private readonly http = inject(HttpClient);

  getFor(studentId: string): Observable<LedgerEntry[]> {
    return this.http.get<LedgerEntry[]>(`/api/students/${studentId}/ledger`);
  }

  /** Treasurer-only - every student's entries in one feed. */
  getFull(): Observable<GlobalLedgerEntry[]> {
    return this.http.get<GlobalLedgerEntry[]>('/api/ledger');
  }
}

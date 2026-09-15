import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Parent } from './models';

@Injectable({ providedIn: 'root' })
export class ParentApiService {
  private readonly http = inject(HttpClient);

  list(): Observable<Parent[]> {
    return this.http.get<Parent[]>('/api/parents');
  }

  get(id: string): Observable<Parent> {
    return this.http.get<Parent>(`/api/parents/${id}`);
  }

  /** The current logged-in account's own Parent record - 404 if the treasurer hasn't created one yet. */
  me(): Observable<Parent> {
    return this.http.get<Parent>('/api/parents/me');
  }

  create(
    firstName: string,
    lastName: string,
    email: string,
    expectedSenderName: string,
    role: 'TREASURER' | 'PARENT' = 'PARENT',
  ): Observable<Parent> {
    return this.http.post<Parent>('/api/parents', { firstName, lastName, email, expectedSenderName, role });
  }

  creditPiggyBank(id: string, amount: number): Observable<Parent> {
    return this.http.post<Parent>(`/api/parents/${id}/piggy-bank/credit`, { amount });
  }
}

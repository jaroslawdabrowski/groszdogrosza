import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Parent, Student } from './models';

@Injectable({ providedIn: 'root' })
export class StudentApiService {
  private readonly http = inject(HttpClient);

  list(): Observable<Student[]> {
    return this.http.get<Student[]>('/api/students');
  }

  get(id: string): Observable<Student> {
    return this.http.get<Student>(`/api/students/${id}`);
  }

  create(firstName: string, lastName: string): Observable<Student> {
    return this.http.post<Student>('/api/students', { firstName, lastName });
  }

  update(id: string, firstName: string, lastName: string): Observable<Student> {
    return this.http.put<Student>(`/api/students/${id}`, { firstName, lastName });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/students/${id}`);
  }

  addParent(
    studentId: string,
    firstName: string,
    lastName: string,
    email: string,
    expectedSenderName: string,
    role: 'TREASURER' | 'PARENT' = 'PARENT',
  ): Observable<Parent> {
    return this.http.post<Parent>(`/api/students/${studentId}/parents`, {
      firstName,
      lastName,
      email,
      expectedSenderName,
      role,
    });
  }

  creditPiggyBank(id: string, amount: number): Observable<Student> {
    return this.http.post<Student>(`/api/students/${id}/piggy-bank/credit`, { amount });
  }
}

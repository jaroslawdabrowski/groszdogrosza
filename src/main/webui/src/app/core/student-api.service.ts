import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CLASS_API } from './class.interceptor';
import { Observable } from 'rxjs';
import { Parent, Student } from './models';

@Injectable({ providedIn: 'root' })
export class StudentApiService {
  private readonly http = inject(HttpClient);

  list(): Observable<Student[]> {
    return this.http.get<Student[]>(`${CLASS_API}/students`);
  }

  get(id: string): Observable<Student> {
    return this.http.get<Student>(`${CLASS_API}/students/${id}`);
  }

  create(firstName: string, lastName: string): Observable<Student> {
    return this.http.post<Student>(`${CLASS_API}/students`, { firstName, lastName });
  }

  update(id: string, firstName: string, lastName: string): Observable<Student> {
    return this.http.put<Student>(`${CLASS_API}/students/${id}`, { firstName, lastName });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${CLASS_API}/students/${id}`);
  }

  addParent(
    studentId: string,
    firstName: string,
    lastName: string,
    email: string,
    expectedSenderName: string,
  ): Observable<Parent> {
    return this.http.post<Parent>(`${CLASS_API}/students/${studentId}/parents`, {
      firstName,
      lastName,
      email,
      expectedSenderName,
    });
  }

  creditPiggyBank(id: string, amount: number): Observable<Student> {
    return this.http.post<Student>(`${CLASS_API}/students/${id}/piggy-bank/credit`, { amount });
  }
}

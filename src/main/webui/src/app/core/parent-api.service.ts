import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Parent } from './models';

/** Creating a parent happens via StudentApiService.addParent instead - a parent is always
 *  added under a specific student, see the backend Parent.studentId javadoc. */
@Injectable({ providedIn: 'root' })
export class ParentApiService {
  private readonly http = inject(HttpClient);

  get(id: string): Observable<Parent> {
    return this.http.get<Parent>(`/api/parents/${id}`);
  }

  /** The current logged-in account's own Parent record - 404 if the treasurer hasn't created one yet. */
  me(): Observable<Parent> {
    return this.http.get<Parent>('/api/parents/me');
  }

  updatePaymentInfo(id: string, bankAccountNumber: string, blikPhoneNumber: string): Observable<Parent> {
    return this.http.put<Parent>(`/api/parents/${id}/payment-info`, { bankAccountNumber, blikPhoneNumber });
  }

  update(id: string, firstName: string, lastName: string, email: string, expectedSenderName: string): Observable<Parent> {
    return this.http.put<Parent>(`/api/parents/${id}`, { firstName, lastName, email, expectedSenderName });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/parents/${id}`);
  }

  /** Creates this parent's Cognito login account - it auto-emails a temporary password. */
  createCognitoAccount(id: string): Observable<void> {
    return this.http.post<void>(`/api/parents/${id}/cognito-account`, {});
  }

  /** Re-sends the invitation e-mail ("I never got it") - only works before the parent has confirmed. */
  resendCognitoInvitation(id: string): Observable<void> {
    return this.http.post<void>(`/api/parents/${id}/cognito-account/resend`, {});
  }
}

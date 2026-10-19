import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CLASS_API } from './class.interceptor';
import { PublicOverview, SchoolClass } from './models';

/** The current class itself (see backend SchoolClassResource). */
@Injectable({ providedIn: 'root' })
export class ClassApiService {
  private readonly http = inject(HttpClient);

  get(): Observable<SchoolClass> {
    return this.http.get<SchoolClass>(CLASS_API);
  }

  /** Start for a logged-in member: totals plus their own child's status. */
  overview(): Observable<PublicOverview> {
    return this.http.get<PublicOverview>(`${CLASS_API}/overview`);
  }

  /** Treasurer-only - where parents send money for every collection of the class. */
  updatePaymentInfo(bankAccountNumber: string, blikPhoneNumber: string): Observable<SchoolClass> {
    return this.http.put<SchoolClass>(`${CLASS_API}/payment-info`, { bankAccountNumber, blikPhoneNumber });
  }
}

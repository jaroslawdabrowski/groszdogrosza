import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PublicOverview } from './models';

/** Unauthenticated - no token is sent for this call (see authInterceptor), and the backend
 * doesn't require one either (see PublicOverviewResource). */
@Injectable({ providedIn: 'root' })
export class PublicApiService {
  private readonly http = inject(HttpClient);

  overview(): Observable<PublicOverview> {
    return this.http.get<PublicOverview>('/api/public/overview');
  }
}

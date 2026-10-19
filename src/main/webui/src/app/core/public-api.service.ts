import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PublicOverview } from './models';

/** Unauthenticated (see backend PublicOverviewResource): a class's totals and payment details. */
@Injectable({ providedIn: 'root' })
export class PublicApiService {
  private readonly http = inject(HttpClient);

  /** The class shown at the app's plain address, without a class link. */
  overview(): Observable<PublicOverview> {
    return this.http.get<PublicOverview>('/api/public/overview');
  }

  /** A class by its public link (`/k/<token>`). */
  overviewByToken(token: string): Observable<PublicOverview> {
    return this.http.get<PublicOverview>(`/api/public/classes/${encodeURIComponent(token)}/overview`);
  }
}

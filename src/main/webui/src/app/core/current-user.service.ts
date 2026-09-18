import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, of, shareReplay, tap, type Observable } from 'rxjs';
import { Parent } from './models';

/**
 * Caches the logged-in account's own Parent record (GET /api/parents/me) so the frontend
 * can show/hide treasurer-only UI (nav links, routes) without waiting on a per-view fetch.
 * This is a UX convenience only - the backend (AuthorizationSupport) is still the actual
 * authorization boundary and re-checks role on every request regardless of what this shows.
 */
@Injectable({ providedIn: 'root' })
export class CurrentUserService {
  private readonly http = inject(HttpClient);

  private me$: Observable<Parent | null> | null = null;
  readonly parent = signal<Parent | null>(null);

  /** Resolves once the current user's Parent record has loaded (or failed to - e.g. not created yet). */
  load(): Observable<Parent | null> {
    if (!this.me$) {
      this.me$ = this.http.get<Parent>('/api/parents/me').pipe(
        tap((parent) => this.parent.set(parent)),
        catchError(() => {
          this.parent.set(null);
          return of(null);
        }),
        shareReplay(1),
      );
    }
    return this.me$;
  }

  isTreasurer(): boolean {
    return this.parent()?.role === 'TREASURER';
  }
}

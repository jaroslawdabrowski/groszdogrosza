import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { catchError, of, shareReplay, tap, type Observable } from 'rxjs';
import { ClassMembership, Me } from './models';

const SELECTED_CLASS_KEY = 'gg-class';

/**
 * The logged-in user (GET /api/me) and the class they're currently looking at. Every
 * class-scoped API call goes to that class (see classInterceptor). A user belongs to one or
 * more classes, with a role per class; the choice is remembered on this device.
 *
 * This only drives what the UI shows - the backend (ClassAccessFilter, ClassContext) decides
 * on every request what the user may actually see and do.
 */
@Injectable({ providedIn: 'root' })
export class CurrentUserService {
  private readonly http = inject(HttpClient);

  private me$: Observable<Me | null> | null = null;
  readonly me = signal<Me | null>(null);
  private readonly selectedClassId = signal<string | null>(readSelectedClass());

  /** The class being shown: the remembered one if the user still belongs to it, else the first. */
  readonly currentClass = computed<ClassMembership | null>(() => {
    const classes = this.me()?.classes ?? [];
    return classes.find((c) => c.id === this.selectedClassId()) ?? classes[0] ?? null;
  });

  /** Resolves once /api/me has loaded (null if it failed - e.g. not logged in). */
  load(): Observable<Me | null> {
    if (!this.me$) {
      this.me$ = this.http.get<Me>('/api/me').pipe(
        tap((me) => this.me.set(me)),
        catchError(() => {
          this.me.set(null);
          return of(null);
        }),
        shareReplay(1),
      );
    }
    return this.me$;
  }

  selectClass(classId: string): void {
    this.selectedClassId.set(classId);
    try {
      localStorage.setItem(SELECTED_CLASS_KEY, classId);
    } catch {
      // Private mode / blocked storage: the choice just isn't remembered.
    }
  }

  classId(): string | null {
    return this.currentClass()?.id ?? null;
  }

  isTreasurer(): boolean {
    return this.currentClass()?.role === 'TREASURER';
  }

  /** The user's own child in the current class - what "Moja skarbonka" points at. */
  studentId(): string | null {
    return [...(this.currentClass()?.studentIds ?? [])].sort()[0] ?? null;
  }

  /** The user's own parent records in the current class (they can't delete themselves). */
  ownParentIds(): string[] {
    return this.currentClass()?.parentIds ?? [];
  }

  firstName(): string | null {
    return this.me()?.firstName ?? null;
  }

  email(): string | null {
    return this.me()?.email ?? null;
  }
}

function readSelectedClass(): string | null {
  try {
    return localStorage.getItem(SELECTED_CLASS_KEY);
  } catch {
    return null;
  }
}

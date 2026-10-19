import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { switchMap, throwError } from 'rxjs';
import { CurrentUserService } from './current-user.service';

/** API services address the current class as `/api/class/...`. */
export const CLASS_API = '/api/class';

/**
 * Rewrites `/api/class/...` to `/api/classes/{current class id}/...` once the current user
 * (and so their class) is known, so the API services never have to thread the class id
 * through every call. A user with no class gets a 404 without the request ever leaving.
 */
export const classInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url !== CLASS_API && !req.url.startsWith(`${CLASS_API}/`)) {
    return next(req);
  }
  const currentUser = inject(CurrentUserService);
  return currentUser.load().pipe(
    switchMap(() => {
      const classId = currentUser.classId();
      if (!classId) {
        return throwError(() => new HttpErrorResponse({ status: 404, statusText: 'No class', url: req.url }));
      }
      const url = `/api/classes/${encodeURIComponent(classId)}${req.url.slice(CLASS_API.length)}`;
      return next(req.clone({ url }));
    }),
  );
};

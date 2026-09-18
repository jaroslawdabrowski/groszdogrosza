import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { CurrentUserService } from './current-user.service';

/** Blocks /treasurer and /ledger client-side for anyone whose own Parent record isn't role TREASURER. */
export const treasurerGuard: CanActivateFn = () => {
  const currentUser = inject(CurrentUserService);
  const router = inject(Router);

  return currentUser.load().pipe(map((parent) => parent?.role === 'TREASURER' || router.createUrlTree(['/dashboard'])));
};

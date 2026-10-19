import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { CurrentUserService } from './current-user.service';

/** Blocks /treasurer and /ledger client-side unless the user is a treasurer of the current
 *  class. A UX convenience - the backend checks the role on every request regardless. */
export const treasurerGuard: CanActivateFn = () => {
  const currentUser = inject(CurrentUserService);
  const router = inject(Router);

  return currentUser.load().pipe(map(() => currentUser.isTreasurer() || router.createUrlTree(['/dashboard'])));
};

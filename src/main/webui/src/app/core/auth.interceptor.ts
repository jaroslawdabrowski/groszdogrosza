import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';

/**
 * Sends the ID token, not the access token, as the bearer credential to our own backend.
 * This is unusual for a "real" API (access tokens are the normal bearer credential), but
 * deliberate here: the backend's whole authorization model (AuthorizationSupport) is built
 * on matching the caller's `email` claim against a Parent record - and Cognito's access
 * token does NOT include the email claim at all (only sub/client_id/scope/...), while the
 * ID token does. Confirmed the hard way against the real deployment: with the access token,
 * every authenticated call resolved to "no matching parent", so no one - including the
 * actual treasurer - could ever pass `requireTreasurer`/`requireSelfOrTreasurer`, even
 * though the exact same code worked fine against local dev's Keycloak (whose access token
 * *does* include email by default - a real, non-obvious difference between the two OIDC
 * providers this app targets). The ID token's `aud` claim also matches
 * `quarkus.oidc.client-id` here, so Quarkus's bearer validation accepts it the same way.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // /api/auth-config must stay unauthenticated - it's what tells us where to get a token from.
  if (!req.url.startsWith('/api') || req.url === '/api/auth-config') {
    return next(req);
  }

  const idToken = inject(OAuthService).getIdToken();
  if (!idToken) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { Authorization: `Bearer ${idToken}` } }));
};

import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // /api/auth-config must stay unauthenticated - it's what tells us where to get a token from.
  if (!req.url.startsWith('/api') || req.url === '/api/auth-config') {
    return next(req);
  }

  const accessToken = inject(OAuthService).getAccessToken();
  if (!accessToken) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } }));
};

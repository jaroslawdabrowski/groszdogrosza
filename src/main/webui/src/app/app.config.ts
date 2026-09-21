import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners, provideZoneChangeDetection, isDevMode } from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { provideOAuthClient } from 'angular-oauth2-oidc';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { provideTranslateService } from '@ngx-translate/core';

import { authInterceptor } from './core/auth.interceptor';
import { AuthService } from './core/auth.service';
import { routes } from './app.routes';
import { provideServiceWorker } from '@angular/service-worker';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    provideOAuthClient(),
    // Must run before the router evaluates authGuard, so it's an app initializer rather
    // than e.g. an effect in App - fetches /api/auth-config (issuer/client differ between
    // local Keycloak and AWS Cognito) and lets angular-oauth2-oidc complete the code-flow
    // redirect round trip if we're returning from a login.
    provideAppInitializer(() => inject(AuthService).init()),
    provideTranslateService({
      lang: 'pl',
      fallbackLang: 'pl',
      loader: provideTranslateHttpLoader({ prefix: '/i18n/', suffix: '.json' }),
    }),
    // Lets the treasurer install this as a home-screen app ("Add to Home Screen"/"Install
    // app") instead of only ever using it through a browser tab. Only registers outside
    // `ng serve` (isDevMode()) - the service worker is built into the production bundle by
    // Quinoa's `ng build`, dev mode has no compiled ngsw-worker.js to register at all.
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};

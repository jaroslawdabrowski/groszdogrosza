import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { firstValueFrom } from 'rxjs';

interface AuthConfigResponse {
  issuer: string;
  clientId: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly oAuthService = inject(OAuthService);

  async init(): Promise<void> {
    const config = await firstValueFrom(this.http.get<AuthConfigResponse>('/api/auth-config'));
    this.oAuthService.configure({
      issuer: config.issuer,
      clientId: config.clientId,
      redirectUri: `${window.location.origin}/`,
      responseType: 'code',
      scope: 'openid',
      // Cognito's discovery document points authorization/token/logout endpoints at the
      // Hosted UI domain (*.auth.<region>.amazoncognito.com), a different host than the
      // issuer itself - fails the library's default strict check. See turboorders'
      // CLAUDE.md (this project mirrors the same OIDC setup) for the full explanation.
      strictDiscoveryDocumentValidation: false,
    });
    await this.oAuthService.loadDiscoveryDocumentAndTryLogin();
    // Without this, the ID/access token's own TTL (24h - see main.tf) is the entire
    // session length: authGuard only checks hasValidAccessToken() locally, and there is no
    // other code anywhere that calls refreshToken() - once the token expired, the only
    // thing that ever happened was a full redirect back to the Hosted UI. This uses the
    // refresh token (issued by Cognito's code-flow token exchange, but never actually used
    // client-side until now) to silently renew the ID/access token in the background ahead
    // of expiry - no iframe involved, that's only needed for the implicit flow's silent
    // refresh, not a plain grant_type=refresh_token call against the token endpoint. Session
    // now effectively lasts up to the 30-day refresh token validity instead of 24h.
    this.oAuthService.setupAutomaticSilentRefresh();
  }

  isAuthenticated(): boolean {
    return this.oAuthService.hasValidAccessToken();
  }

  login(): void {
    this.oAuthService.initCodeFlow();
  }

  logout(): void {
    // Builds the logout URL manually with BOTH the standard OIDC RP-initiated-logout
    // params AND Cognito's own non-standard ones, so the same code path works against
    // both Keycloak (local dev) and Cognito (AWS) - each ignores what it doesn't recognize.
    const idToken = this.oAuthService.getIdToken();
    const redirectUri = `${window.location.origin}/`;
    const logoutUrl = this.oAuthService.logoutUrl;
    const params = new URLSearchParams({
      client_id: this.oAuthService.clientId ?? '',
      logout_uri: redirectUri,
      post_logout_redirect_uri: redirectUri,
      id_token_hint: idToken,
    });

    this.oAuthService.logOut(true); // clear local tokens only, skip the library's own redirect
    window.location.href = `${logoutUrl}?${params.toString()}`;
  }
}

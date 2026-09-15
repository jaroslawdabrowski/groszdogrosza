import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth.service';

/**
 * Not a real credential form - login itself always happens on the IdP's own Hosted UI
 * (Cognito) / login page (Keycloak), same as the sibling "turboorders" project. This page
 * exists as an explicit, non-guarded landing point that starts that redirect on demand
 * (e.g. after a logout), rather than relying purely on authGuard's implicit redirect.
 */
@Component({
  selector: 'app-login',
  imports: [MatCardModule, MatButtonModule, TranslatePipe],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly authService = inject(AuthService);

  login(): void {
    this.authService.login();
  }
}

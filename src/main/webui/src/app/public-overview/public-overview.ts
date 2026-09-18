import { Component, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatButtonModule } from '@angular/material/button';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth.service';
import { PublicApiService } from '../core/public-api.service';
import { PublicOverview as PublicOverviewModel } from '../core/models';

/**
 * The unauthenticated landing page (route "/") - shows every currently active collection's
 * progress and how to pay (bank account / BLIK phone), with no login required. Per-parent
 * names/amounts are deliberately NOT here - see backend PublicOverviewResource's javadoc.
 * Logging in (top-right) is what a parent needs to see their own piggy bank/ledger.
 */
@Component({
  selector: 'app-public-overview',
  imports: [MatCardModule, MatProgressBarModule, MatButtonModule, TranslatePipe],
  templateUrl: './public-overview.html',
  styleUrl: './public-overview.scss',
})
export class PublicOverview {
  private readonly publicApi = inject(PublicApiService);
  readonly authService = inject(AuthService);

  readonly overview = signal<PublicOverviewModel | null>(null);
  readonly loading = signal(true);

  constructor() {
    this.publicApi.overview().subscribe({
      next: (overview) => {
        this.overview.set(overview);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  login(): void {
    this.authService.login();
  }
}

import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth.service';
import { PublicApiService } from '../core/public-api.service';
import { PublicOverview as PublicOverviewModel } from '../core/models';
import { Logo } from '../shared/logo/logo';
import { LoadingSpinner } from '../shared/loading-spinner/loading-spinner';
import { MyStudentStatusBadge } from '../shared/my-student-status-badge/my-student-status-badge';

/**
 * The unauthenticated landing page (route "/") - shows every currently active collection's
 * progress and how to pay (bank account / BLIK phone), with no login required. Per-parent
 * names/amounts are deliberately NOT here - see backend PublicOverviewResource's javadoc.
 * Logging in (top-right) is what a parent needs to see their own piggy bank/ledger.
 */
@Component({
  selector: 'app-public-overview',
  imports: [RouterLink, ClipboardModule, MatCardModule, MatProgressBarModule, MatButtonModule, MatIconModule, MatTooltipModule, TranslatePipe, Logo, LoadingSpinner, MyStudentStatusBadge],
  templateUrl: './public-overview.html',
  styleUrl: './public-overview.scss',
})
export class PublicOverview {
  private readonly publicApi = inject(PublicApiService);
  readonly authService = inject(AuthService);

  readonly overview = signal<PublicOverviewModel | null>(null);
  readonly loading = signal(true);
  /** Which payment field was just copied ('bank' | 'blik'), briefly, to swap its icon to a
   *  checkmark - reset after a short delay rather than tracked per-click state machinery. */
  readonly justCopied = signal<'bank' | 'blik' | null>(null);

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

  onCopied(field: 'bank' | 'blik'): void {
    this.justCopied.set(field);
    setTimeout(() => {
      if (this.justCopied() === field) {
        this.justCopied.set(null);
      }
    }, 1500);
  }
}

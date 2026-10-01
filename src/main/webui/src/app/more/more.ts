import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth.service';
import { CurrentUserService } from '../core/current-user.service';
import { LanguageService, SUPPORTED_LANGUAGES, type Language } from '../core/language.service';

/** The phone tab bar's "Więcej": the destinations used too rarely for a tab of their own.
 *  On desktop the sidebar already shows all of these. */
@Component({
  selector: 'app-more',
  imports: [RouterLink, MatIconModule, TranslatePipe],
  template: `
    <div class="page">
      <header class="page-head">
        <h1>{{ 'more.title' | translate }}</h1>
        @if (email(); as e) {
          <p class="sub">{{ 'more.account' | translate: { email: e } }}</p>
        }
      </header>

      <div class="panel panel--flush">
        <div class="list">
          @if (currentUser.isTreasurer()) {
            <a class="list-row" routerLink="/ledger">
              <span class="list-icon list-icon--blue"><mat-icon>history</mat-icon></span>
              <span class="list-main"><span class="list-title">{{ 'nav.ledger' | translate }}</span></span>
              <mat-icon class="list-chevron">chevron_right</mat-icon>
            </a>
          }
          <a class="list-row" routerLink="/jak-to-dziala">
            <span class="list-icon list-icon--gold"><mat-icon>help</mat-icon></span>
            <span class="list-main"><span class="list-title">{{ 'nav.howItWorks' | translate }}</span></span>
            <mat-icon class="list-chevron">chevron_right</mat-icon>
          </a>
          <div class="list-row">
            <span class="list-icon"><mat-icon>translate</mat-icon></span>
            <span class="list-main"><span class="list-title">{{ 'more.language' | translate }}</span></span>
            <span class="list-end">
              <span class="lang" role="group" [attr.aria-label]="'more.language' | translate">
                @for (language of languages; track language) {
                  <button type="button" [attr.aria-pressed]="current() === language" (click)="change(language)">{{ language.toUpperCase() }}</button>
                }
              </span>
            </span>
          </div>
        </div>
      </div>

      <button type="button" class="btn btn--danger btn--block" (click)="logout()">
        <mat-icon>logout</mat-icon>{{ 'nav.logout' | translate }}
      </button>
    </div>
  `,
  styles: `
    .lang {
      display: inline-flex;
      padding: 3px;
      gap: 2px;
      border-radius: var(--radius-full);
      background: #e7ebf4;
    }
    .lang button {
      -webkit-appearance: none;
      appearance: none;
      min-width: 44px;
      min-height: 34px;
      border: 0;
      border-radius: var(--radius-full);
      background: transparent;
      color: var(--ink-2);
      font: 700 var(--text-xs) var(--font-body);
      cursor: pointer;
    }
    .lang button[aria-pressed='true'] {
      background: var(--surface);
      color: var(--ink);
      box-shadow: 0 1px 2px rgba(28, 42, 92, 0.16);
    }
  `,
})
export class More {
  private readonly authService = inject(AuthService);
  private readonly languageService = inject(LanguageService);
  readonly currentUser = inject(CurrentUserService);
  readonly languages = SUPPORTED_LANGUAGES;

  email(): string | null {
    return this.currentUser.parent()?.email ?? null;
  }

  current(): Language {
    return this.languageService.current();
  }

  change(language: Language): void {
    this.languageService.change(language);
  }

  logout(): void {
    this.authService.logout();
  }
}

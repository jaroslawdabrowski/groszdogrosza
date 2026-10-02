import { Component, OnDestroy, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';

const DISMISSED_KEY = 'gg-install-hint-dismissed';

/** Chrome/Android's install event - not in the DOM typings. */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

/**
 * "Dodaj aplikację do ekranu początkowego" - a one-off hint for logged-in users, so the app
 * opens like an installed one (its own icon, splash, no browser bars). Shown only when the
 * app isn't already running installed, and never again once dismissed or installed.
 *
 * - Android / desktop Chrome: catches `beforeinstallprompt` and offers a real "Zainstaluj"
 *   button that opens the system install dialog.
 * - iPhone / iPad Safari has no such event, so the hint explains the two taps instead
 *   (Share, then "Do ekranu początkowego").
 * - Anything else: no hint at all - there's nothing useful to say.
 */
@Component({
  selector: 'app-install-hint',
  imports: [MatIconModule, TranslatePipe],
  template: `
    @if (mode() !== 'none') {
      <aside class="install-hint gg-enter" data-testid="install-hint" [attr.aria-label]="'install.title' | translate">
        <span class="list-icon list-icon--blue" aria-hidden="true"><mat-icon>install_mobile</mat-icon></span>
        <div class="install-text">
          <p class="install-title">{{ 'install.title' | translate }}</p>
          @if (mode() === 'ios') {
            <p class="install-body">
              {{ 'install.iosStep1' | translate }}
              <mat-icon class="inline-icon" aria-hidden="true">ios_share</mat-icon>
              {{ 'install.iosStep2' | translate }}
            </p>
          } @else {
            <p class="install-body">{{ 'install.body' | translate }}</p>
            <button type="button" class="btn btn--primary btn--sm install-btn" (click)="install()">
              <mat-icon>download</mat-icon>{{ 'install.button' | translate }}
            </button>
          }
        </div>
        <button type="button" class="icon-btn" [attr.aria-label]="'install.dismiss' | translate" (click)="dismiss()">
          <mat-icon>close</mat-icon>
        </button>
      </aside>
    }
  `,
  styles: `
    .install-hint {
      display: flex;
      align-items: flex-start;
      gap: var(--space-3);
      padding: var(--space-4);
      border-radius: var(--radius-card);
      border: 1.5px dashed var(--ink-blue-200);
      background: var(--ink-blue-50);
    }
    .install-text {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
    }
    .install-title {
      font-weight: 700;
      color: var(--ink);
    }
    .install-body {
      color: var(--ink-2);
      font-size: var(--text-sm);
      line-height: 1.5;
    }
    .inline-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      vertical-align: -3px;
      color: var(--ink-blue-500);
    }
    .install-btn {
      align-self: flex-start;
      margin-top: var(--space-1);
    }
    .icon-btn {
      margin: calc(var(--space-2) * -1) calc(var(--space-2) * -1) 0 0;
    }
  `,
})
export class InstallHint implements OnDestroy {
  readonly mode = signal<'none' | 'ios' | 'prompt'>('none');
  private deferredPrompt: BeforeInstallPromptEvent | null = null;

  private readonly onBeforeInstall = (event: Event) => {
    event.preventDefault();
    this.deferredPrompt = event as BeforeInstallPromptEvent;
    if (!this.wasDismissed()) {
      this.mode.set('prompt');
    }
  };

  private readonly onInstalled = () => this.dismiss();

  constructor() {
    if (this.isInstalled() || this.wasDismissed()) {
      return;
    }
    if (this.isIosSafari()) {
      this.mode.set('ios');
      return;
    }
    window.addEventListener('beforeinstallprompt', this.onBeforeInstall);
    window.addEventListener('appinstalled', this.onInstalled);
  }

  ngOnDestroy(): void {
    window.removeEventListener('beforeinstallprompt', this.onBeforeInstall);
    window.removeEventListener('appinstalled', this.onInstalled);
  }

  async install(): Promise<void> {
    const prompt = this.deferredPrompt;
    if (!prompt) {
      return;
    }
    await prompt.prompt();
    const choice = await prompt.userChoice;
    this.deferredPrompt = null;
    if (choice.outcome === 'accepted') {
      this.dismiss();
    } else {
      this.mode.set('none');
    }
  }

  dismiss(): void {
    this.mode.set('none');
    try {
      localStorage.setItem(DISMISSED_KEY, '1');
    } catch {
      // Private mode / blocked storage: the hint just comes back next time.
    }
  }

  private wasDismissed(): boolean {
    try {
      return localStorage.getItem(DISMISSED_KEY) === '1';
    } catch {
      return false;
    }
  }

  private isInstalled(): boolean {
    const iosStandalone = (navigator as Navigator & { standalone?: boolean }).standalone === true;
    return iosStandalone || window.matchMedia('(display-mode: standalone)').matches;
  }

  /** Safari on iPhone/iPad - the only browser there that can add to the home screen in the
   *  way that keeps this app's manifest (iPadOS reports itself as a Mac, hence the touch check). */
  private isIosSafari(): boolean {
    const ua = navigator.userAgent;
    const iDevice = /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
    const safari = /Safari/.test(ua) && !/CriOS|FxiOS|EdgiOS|OPiOS/.test(ua);
    return iDevice && safari;
  }
}

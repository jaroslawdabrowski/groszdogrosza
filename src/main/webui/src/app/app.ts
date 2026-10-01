import { Component, OnInit, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, NavigationError, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map, take } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from './core/auth.service';
import { CurrentUserService } from './core/current-user.service';
import { LanguageService, SUPPORTED_LANGUAGES, type Language } from './core/language.service';
import { Logo } from './shared/logo/logo';
import { hideSplash } from './core/splash';

type TabId = 'start' | 'collections' | 'piggy' | 'class' | 'more' | 'ledger' | 'how';

interface NavItem {
  id: TabId;
  link: string | string[];
  icon: string;
  label: string;
  shortLabel: string;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, MatIconModule, TranslatePipe, Logo],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly languageService = inject(LanguageService);
  private readonly currentUser = inject(CurrentUserService);
  private readonly router = inject(Router);

  readonly languages = SUPPORTED_LANGUAGES;

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** Which destination the current URL belongs to - a collection page counts as "Zbiórki",
   *  a student page as "Skarbonka" only when it's the account's own child. */
  readonly activeTab = computed<TabId | null>(() => {
    const path = this.url().split(/[?#]/)[0];
    if (path === '/') return 'start';
    if (path === '/dashboard' || path.startsWith('/collections/')) return 'collections';
    if (path === '/treasurer') return 'class';
    if (path === '/ledger') return 'ledger';
    if (path === '/wiecej') return 'more';
    if (path === '/jak-to-dziala') return 'how';
    if (path.startsWith('/students/')) {
      return path === `/students/${this.currentUser.studentId()}` ? 'piggy' : 'class';
    }
    return null;
  });

  /** The phone tab bar: the 3-4 most-used destinations, then "Więcej" for the rest. */
  readonly navItems = computed<NavItem[]>(() => {
    const items: NavItem[] = [
      { id: 'start', link: '/', icon: 'home', label: 'nav.start', shortLabel: 'nav.start' },
      { id: 'collections', link: '/dashboard', icon: 'receipt_long', label: 'nav.dashboard', shortLabel: 'nav.dashboard' },
    ];
    const studentId = this.currentUser.studentId();
    if (studentId) {
      items.push({ id: 'piggy', link: ['/students', studentId], icon: 'savings', label: 'nav.myWallet', shortLabel: 'nav.piggyShort' });
    }
    if (this.currentUser.isTreasurer()) {
      items.push({ id: 'class', link: '/treasurer', icon: 'groups', label: 'nav.treasurer', shortLabel: 'nav.classShort' });
    }
    items.push({ id: 'more', link: '/wiecej', icon: 'more_horiz', label: 'nav.more', shortLabel: 'nav.more' });
    return items;
  });

  /** The desktop sidebar shows everything at once, so it has no "Więcej" entry of its own. */
  readonly sideItems = computed(() => this.navItems().filter((item) => item.id !== 'more'));

  readonly onLoginPage = computed(() => this.url().startsWith('/login'));

  constructor() {
    // Only a completed (or failed) navigation hides the launch splash - not a cancelled one:
    // authGuard cancels navigation for a logged-out visitor and redirects to the login page,
    // and keeping the splash up until the browser actually leaves avoids flashing an empty
    // app shell in between.
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd || event instanceof NavigationError),
        take(1),
      )
      .subscribe(() => hideSplash());
  }

  ngOnInit(): void {
    this.languageService.init();
    if (this.isAuthenticated()) {
      this.currentUser.load().subscribe();
    }
  }

  isTreasurer(): boolean {
    return this.currentUser.isTreasurer();
  }

  currentLanguage(): Language {
    return this.languageService.current();
  }

  changeLanguage(language: Language): void {
    this.languageService.change(language);
  }

  isAuthenticated(): boolean {
    return this.authService.isAuthenticated();
  }

  login(): void {
    this.authService.login();
  }

  logout(): void {
    this.authService.logout();
  }
}

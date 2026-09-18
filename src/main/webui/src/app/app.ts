import { Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from './core/auth.service';
import { CurrentUserService } from './core/current-user.service';
import { LanguageService, SUPPORTED_LANGUAGES, type Language } from './core/language.service';
import { Logo } from './shared/logo/logo';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, MatToolbarModule, MatButtonModule, MatIconModule, MatMenuModule, TranslatePipe, Logo],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly languageService = inject(LanguageService);
  private readonly currentUser = inject(CurrentUserService);

  readonly languages = SUPPORTED_LANGUAGES;

  ngOnInit(): void {
    this.languageService.init();
    if (this.isAuthenticated()) {
      this.currentUser.load().subscribe();
    }
  }

  isTreasurer(): boolean {
    return this.currentUser.isTreasurer();
  }

  /** Null until GET /api/parents/me resolves, or if the treasurer hasn't created a matching
   *  Parent (and its linked Student) record for this account yet - the "Moja skarbonka" nav
   *  link only makes sense once there's actually a student to link to. */
  myStudentId(): string | null {
    return this.currentUser.studentId();
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

  logout(): void {
    this.authService.logout();
  }
}

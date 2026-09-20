import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';

/**
 * A static, unauthenticated explainer for parents - what "Grosz do Grosza" actually does,
 * in plain language, and how to get an account. Reachable from the toolbar's menu (visible
 * whether logged in or not - see app.html) and from a link on PublicOverview next to the
 * payment info, since that's where a parent who's never used the app before is most likely
 * to land first. Deliberately just static copy (no signals, no API calls) - if the
 * underlying business rules change, update this page's text alongside them, the same way
 * CLAUDE.md gets updated alongside code.
 */
@Component({
  selector: 'app-how-it-works',
  imports: [RouterLink, MatCardModule, MatIconModule, TranslatePipe],
  templateUrl: './how-it-works.html',
  styleUrl: './how-it-works.scss',
})
export class HowItWorks {}

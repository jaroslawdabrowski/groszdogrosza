import { Component, computed, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth.service';
import { CurrentUserService } from '../core/current-user.service';
import { PublicApiService } from '../core/public-api.service';
import { CollectionApiService } from '../core/collection-api.service';
import { StudentApiService } from '../core/student-api.service';
import { CollectionProgress, CollectionSummary, PublicOverview as PublicOverviewModel, Student } from '../core/models';
import { LoadingSpinner } from '../shared/loading-spinner/loading-spinner';
import { RosterDots } from '../shared/roster-dots/roster-dots';
import { MoneyPipe } from '../shared/money.pipe';

type CopyField = 'bank' | 'blik' | 'title';

/**
 * Start ("/"). For anyone, logged in or not: every ACTIVE collection's progress (aggregate
 * only - see backend PublicOverviewResource) and how to pay. For a logged-in parent it also
 * puts their OWN child first: what's still owed in each collection, earlier collections, and
 * the child's piggy bank.
 */
@Component({
  selector: 'app-public-overview',
  imports: [NgTemplateOutlet, RouterLink, ClipboardModule, MatIconModule, TranslatePipe, LoadingSpinner, RosterDots, MoneyPipe],
  templateUrl: './public-overview.html',
  styleUrl: './public-overview.scss',
})
export class PublicOverview {
  private readonly publicApi = inject(PublicApiService);
  private readonly collectionApi = inject(CollectionApiService);
  private readonly studentApi = inject(StudentApiService);
  private readonly currentUser = inject(CurrentUserService);
  readonly authService = inject(AuthService);

  readonly overview = signal<PublicOverviewModel | null>(null);
  readonly loading = signal(true);
  readonly child = signal<Student | null>(null);
  readonly allCollections = signal<CollectionSummary[]>([]);
  /** Which payment field was just copied, briefly, to swap its icon to a check. */
  readonly justCopied = signal<CopyField | null>(null);

  readonly parentName = computed(() => this.currentUser.parent()?.firstName ?? null);
  /** Settled (and any other non-active) collections, newest first - the active ones are
   *  already shown as sheets above. */
  readonly earlierCollections = computed(() =>
    this.allCollections()
      .filter((c) => c.status !== 'ACTIVE')
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, 3),
  );

  constructor() {
    this.publicApi.overview().subscribe({
      next: (overview) => {
        this.overview.set(overview);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    if (this.authService.isAuthenticated()) {
      this.collectionApi.list().subscribe((collections) => this.allCollections.set(collections));
      this.currentUser.load().subscribe((parent) => {
        if (parent?.studentId) {
          this.studentApi.get(parent.studentId).subscribe((student) => this.child.set(student));
        }
      });
    }
  }

  isLoggedIn(): boolean {
    return this.authService.isAuthenticated();
  }

  hasPaymentInfo(o: PublicOverviewModel): boolean {
    return !!o.paymentInfo && !!(o.paymentInfo.bankAccountNumber || o.paymentInfo.blikPhoneNumber);
  }

  /** What's still owed for the viewer's own child in this collection (0 once paid). */
  owed(c: CollectionProgress): number {
    const required = c.collection.myStudentRequiredAmount ?? 0;
    const paid = c.collection.myStudentPaidAmount ?? 0;
    return Math.max(0, Math.round((required - paid) * 100) / 100);
  }


  /** Everything the viewer's own child still owes across active collections - one transfer
   *  can cover it all (the backend sweeps money into the oldest collection first). */
  totalOwed(o: PublicOverviewModel): number {
    const sum = o.activeCollections
      .filter((c) => c.collection.myStudentStatus === 'PENDING')
      .reduce((total, c) => total + this.owed(c), 0);
    return Math.round(sum * 100) / 100;
  }

  /** The amount the card suggests paying ahead: 100 zł, or - when more than that is already
   *  due - the next round 50 above it. Paying ahead is the whole point of the piggy bank:
   *  one bigger transfer that later collections draw from, instead of a transfer per
   *  collection. */
  suggestedPrepay(o: PublicOverviewModel): number {
    const due = this.isLoggedIn() ? this.totalOwed(o) : 0;
    return due < 100 ? 100 : Math.ceil((due + 1) / 50) * 50;
  }

  login(): void {
    this.authService.login();
  }

  onCopied(field: CopyField): void {
    this.justCopied.set(field);
    setTimeout(() => {
      if (this.justCopied() === field) {
        this.justCopied.set(null);
      }
    }, 1500);
  }
}

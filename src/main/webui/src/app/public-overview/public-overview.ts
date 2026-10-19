import { Component, computed, inject, signal } from '@angular/core';
import { Observable, switchMap } from 'rxjs';
import { NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth.service';
import { CurrentUserService } from '../core/current-user.service';
import { PublicApiService } from '../core/public-api.service';
import { ClassApiService } from '../core/class-api.service';
import { CollectionApiService } from '../core/collection-api.service';
import { StudentApiService } from '../core/student-api.service';
import { CollectionProgress, CollectionSummary, PublicOverview as PublicOverviewModel, Student } from '../core/models';
import { LoadingSpinner } from '../shared/loading-spinner/loading-spinner';
import { RosterDots } from '../shared/roster-dots/roster-dots';
import { MoneyPipe } from '../shared/money.pipe';
import { InstallHint } from '../shared/install-hint/install-hint';

type CopyField = 'bank' | 'blik' | 'title';

/** One item on the treasurer's "Do zrobienia" list - see PublicOverview.todos. */
interface TodoItem {
  key: string;
  icon: string;
  textKey: string;
  params: Record<string, string | number>;
  link: string[];
  query?: Record<string, string>;
}

/**
 * Start ("/"). For anyone, logged in or not: every ACTIVE collection's progress (aggregate
 * only - see backend PublicOverviewResource) and how to pay. For a logged-in parent it also
 * puts their OWN child first: what's still owed in each collection, earlier collections, and
 * the child's piggy bank.
 */
@Component({
  selector: 'app-public-overview',
  imports: [NgTemplateOutlet, RouterLink, ClipboardModule, MatIconModule, TranslatePipe, LoadingSpinner, RosterDots, MoneyPipe, InstallHint],
  templateUrl: './public-overview.html',
  styleUrl: './public-overview.scss',
})
export class PublicOverview {
  private readonly publicApi = inject(PublicApiService);
  private readonly classApi = inject(ClassApiService);
  /** Set on a class's public link (`/k/<token>`): that class's public view, nothing else. */
  readonly publicToken = inject(ActivatedRoute).snapshot.paramMap.get('token');
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

  readonly parentName = computed(() => this.currentUser.firstName());
  /** Settled (and any other non-active) collections, newest first - the active ones are
   *  already shown as sheets above. */
  readonly earlierCollections = computed(() =>
    this.allCollections()
      .filter((c) => c.status !== 'ACTIVE')
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, 3),
  );

  /** Every student in the class - only loaded for the treasurer (the endpoint is
   *  treasurer-only), for the "Do zrobienia" list. */
  readonly students = signal<Student[]>([]);

  /** The treasurer's "Do zrobienia": things on Start that need the treasurer, not a parent.
   *  Collections everyone has paid for (ready to settle), missing payment details, and
   *  students with no parent attached (nobody can log in for them, and a transfer from a
   *  grandparent's account can't be matched through a parent). Empty = the section hides. */
  readonly todos = computed<TodoItem[]>(() => {
    if (!this.isTreasurer()) {
      return [];
    }
    const items: TodoItem[] = [];
    const o = this.overview();
    for (const c of o?.activeCollections ?? []) {
      if (c.studentsCount > 0 && c.studentsPaidCount === c.studentsCount) {
        items.push({ key: `settle-${c.collection.id}`, icon: 'task_alt', textKey: 'start.todo.readyToSettle',
          params: { title: c.collection.title }, link: ['/collections', c.collection.id] });
      }
    }
    if (o && !this.hasPaymentInfo(o)) {
      items.push({ key: 'payment', icon: 'account_balance', textKey: 'start.todo.noPaymentInfo', params: {},
        link: ['/treasurer'], query: { tab: 'payment' } });
    }
    const orphans = this.students().filter((s) => s.parents.length === 0).length;
    if (orphans > 0) {
      items.push({ key: 'orphans', icon: 'person_add', textKey: 'start.todo.noParent', params: { count: orphans },
        link: ['/treasurer'] });
    }
    return items;
  });

  isTreasurer(): boolean {
    return this.currentUser.isTreasurer();
  }

  constructor() {
    this.loadOverview();
    if (this.showsOwnClass()) {
      this.currentUser.load().subscribe(() => {
        if (!this.currentUser.classId()) {
          return;
        }
        this.collectionApi.list().subscribe((collections) => this.allCollections.set(collections));
        const studentId = this.currentUser.studentId();
        if (studentId) {
          this.studentApi.get(studentId).subscribe((student) => this.child.set(student));
        }
        if (this.currentUser.isTreasurer()) {
          this.studentApi.list().subscribe((students) => this.students.set(students));
        }
      });
    }
  }

  /** Set when the overview couldn't be loaded - the page then says so and offers a retry,
   *  instead of showing an empty page with no collections and no payment details. */
  readonly loadFailed = signal(false);

  loadOverview(): void {
    this.loading.set(true);
    this.loadFailed.set(false);
    this.overviewSource().subscribe({
      next: (overview) => {
        this.overview.set(overview);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadFailed.set(true);
      },
    });
  }

  isLoggedIn(): boolean {
    return this.authService.isAuthenticated();
  }

  /** Start about the user's own class, with their child, links into the class and so on:
   *  logged in, a member of a class, and on the plain address - not on a class link, which
   *  always shows exactly the public view. */
  memberView(): boolean {
    return this.showsOwnClass() && !!this.currentUser.classId();
  }

  /** Logged in, on the plain address (not a class link): Start is about the user's own class. */
  private showsOwnClass(): boolean {
    return !this.publicToken && this.isLoggedIn();
  }

  /**
   * Which overview this page shows: a class link shows that class's public view; a logged-in
   * member sees their current class; anyone else (or someone who belongs to no class yet)
   * sees the class at the app's plain address.
   */
  private overviewSource(): Observable<PublicOverviewModel> {
    if (this.publicToken) {
      return this.publicApi.overviewByToken(this.publicToken);
    }
    if (!this.isLoggedIn()) {
      return this.publicApi.overview();
    }
    return this.currentUser.load().pipe(
      switchMap(() => (this.currentUser.classId() ? this.classApi.overview() : this.publicApi.overview())),
    );
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
    const due = this.memberView() ? this.totalOwed(o) : 0;
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

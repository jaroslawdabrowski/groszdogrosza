import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { StudentApiService } from '../core/student-api.service';
import { LedgerApiService } from '../core/ledger-api.service';
import { CurrentUserService } from '../core/current-user.service';
import { LedgerEntry, Student } from '../core/models';
import { LoadingSpinner } from '../shared/loading-spinner/loading-spinner';

/** "Moja skarbonka" for a parent, or a per-student drill-down for the treasurer - a
 *  student's own piggy bank balance and ledger, plus (read-only here) their linked
 *  parents' contact info. Editing a parent's details happens in TreasurerPanel. */
@Component({
  selector: 'app-student-view',
  imports: [RouterLink, MatCardModule, MatIconModule, TranslatePipe, LoadingSpinner],
  templateUrl: './student-view.html',
  styleUrl: './student-view.scss',
})
export class StudentView {
  private readonly route = inject(ActivatedRoute);
  private readonly studentApi = inject(StudentApiService);
  private readonly ledgerApi = inject(LedgerApiService);
  private readonly currentUser = inject(CurrentUserService);
  private readonly translate = inject(TranslateService);

  readonly student = signal<Student | null>(null);
  readonly ledger = signal<LedgerEntry[]>([]);
  readonly loading = signal(true);

  isTreasurer(): boolean {
    return this.currentUser.isTreasurer();
  }

  constructor() {
    const studentId = this.route.snapshot.paramMap.get('id')!;
    this.studentApi.get(studentId).subscribe({
      next: (student) => {
        this.student.set(student);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.ledgerApi.getFor(studentId).subscribe((entries) => this.ledger.set(entries));
  }

  /** Translation key for one ledger entry - see LedgerEventType javadoc: the backend
   *  ships an enum + params, rendering the sentence is this frontend's job via i18n. */
  translationKeyFor(entry: LedgerEntry): string {
    return 'ledger.' + entry.eventType;
  }

  /** Formatted in the app's currently-chosen language, not the browser's own locale - same
   *  reasoning as CollectionDetails.printedOnLabel. Includes the time, not just the date -
   *  more than one event can land on the same day. */
  dateLabel(occurredAt: string): string {
    const locale = this.translate.currentLang() === 'en' ? 'en-US' : 'pl-PL';
    return new Date(occurredAt).toLocaleString(locale, {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  iconFor(entry: LedgerEntry): string {
    switch (entry.eventType) {
      case 'CONTRIBUTION_RECEIVED':
        return 'south_west';
      case 'COLLECTION_SETTLED':
        return 'celebration';
      case 'PIGGY_BANK_CREDITED':
        return 'savings';
      case 'PIGGY_BANK_APPLIED_TO_COLLECTION':
        return 'north_east';
      default:
        return 'receipt_long';
    }
  }
}

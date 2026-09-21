import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LedgerApiService } from '../core/ledger-api.service';
import { GlobalLedgerEntry } from '../core/models';
import { LoadingSpinner } from '../shared/loading-spinner/loading-spinner';

/** Treasurer-only "log wszystkich transakcji" - GET /api/ledger returns 403 for anyone
 * else, which the backend enforces regardless of this page even being reachable. */
@Component({
  selector: 'app-global-ledger',
  imports: [RouterLink, MatCardModule, MatIconModule, TranslatePipe, LoadingSpinner],
  templateUrl: './global-ledger.html',
  styleUrl: './global-ledger.scss',
})
export class GlobalLedger {
  private readonly ledgerApi = inject(LedgerApiService);
  private readonly translate = inject(TranslateService);

  readonly entries = signal<GlobalLedgerEntry[]>([]);
  readonly loading = signal(true);
  readonly forbidden = signal(false);

  constructor() {
    this.ledgerApi.getFull().subscribe({
      next: (entries) => {
        this.entries.set(entries);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 403) {
          this.forbidden.set(true);
        }
      },
    });
  }

  translationKeyFor(entry: GlobalLedgerEntry): string {
    return 'ledger.' + entry.eventType;
  }

  /** Formatted in the app's currently-chosen language, not the browser's own locale - same
   *  reasoning as CollectionDetails.printedOnLabel. Includes the time, not just the date -
   *  more than one event can land on the same day (see the Deręgowski double-credit this
   *  was added to help spot), so the date alone wouldn't have been enough to tell them apart. */
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

  iconFor(entry: GlobalLedgerEntry): string {
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

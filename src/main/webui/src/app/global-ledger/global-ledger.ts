import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';
import { LedgerApiService } from '../core/ledger-api.service';
import { GlobalLedgerEntry } from '../core/models';

/** Treasurer-only "log wszystkich transakcji" - GET /api/ledger returns 403 for anyone
 * else, which the backend enforces regardless of this page even being reachable. */
@Component({
  selector: 'app-global-ledger',
  imports: [RouterLink, MatCardModule, MatIconModule, TranslatePipe],
  templateUrl: './global-ledger.html',
  styleUrl: './global-ledger.scss',
})
export class GlobalLedger {
  private readonly ledgerApi = inject(LedgerApiService);

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

import { Component, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LedgerApiService } from '../core/ledger-api.service';
import { GlobalLedgerEntry } from '../core/models';
import { LoadingSpinner } from '../shared/loading-spinner/loading-spinner';
import { ledgerIcon, ledgerTone, localeFor } from '../shared/ledger-format';

/** Treasurer-only "log wszystkich transakcji" - GET /api/ledger returns 403 for anyone
 * else, which the backend enforces regardless of this page even being reachable. */
@Component({
  selector: 'app-global-ledger',
  imports: [MatIconModule, TranslatePipe, LoadingSpinner],
  templateUrl: './global-ledger.html',
  styleUrl: './global-ledger.scss',
})
export class GlobalLedger {
  private readonly ledgerApi = inject(LedgerApiService);
  private readonly translate = inject(TranslateService);

  readonly entries = signal<GlobalLedgerEntry[]>([]);
  readonly loading = signal(true);
  readonly forbidden = signal(false);

  /** Entries grouped by calendar day (already newest first from the backend), so a long feed
   *  reads like a diary rather than one undifferentiated list. */
  readonly days = computed(() => {
    const locale = localeFor(this.translate.currentLang());
    const groups: { label: string; entries: GlobalLedgerEntry[] }[] = [];
    for (const entry of this.entries()) {
      const label = new Date(entry.occurredAt).toLocaleDateString(locale, { day: 'numeric', month: 'long', year: 'numeric' });
      const last = groups[groups.length - 1];
      if (last && last.label === label) {
        last.entries.push(entry);
      } else {
        groups.push({ label, entries: [entry] });
      }
    }
    return groups;
  });

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

  /** Time only - the day is the group heading above it. Still shown because more than one
   *  event can land on the same day (see the Deręgowski double-credit this helped spot). */
  timeLabel(occurredAt: string): string {
    return new Date(occurredAt).toLocaleTimeString(localeFor(this.translate.currentLang()), { hour: '2-digit', minute: '2-digit' });
  }

  readonly iconFor = ledgerIcon;
  readonly toneFor = ledgerTone;
}

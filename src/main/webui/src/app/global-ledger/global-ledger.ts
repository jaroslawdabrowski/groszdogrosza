import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LedgerApiService } from '../core/ledger-api.service';
import { GlobalLedgerEntry } from '../core/models';
import { LoadingSpinner } from '../shared/loading-spinner/loading-spinner';
import { ledgerIcon, ledgerTone, localeFor } from '../shared/ledger-format';

/** The log's type filter - groups of LedgerEventType a treasurer actually asks about. */
type LedgerKind = 'all' | 'payments' | 'piggy' | 'settlements';

const KIND_EVENTS: Record<Exclude<LedgerKind, 'all'>, string[]> = {
  payments: ['CONTRIBUTION_RECEIVED'],
  piggy: ['PIGGY_BANK_CREDITED', 'PIGGY_BANK_APPLIED_TO_COLLECTION'],
  settlements: ['COLLECTION_SETTLED', 'REMOVED_FROM_COLLECTION'],
};

/** Treasurer-only "log wszystkich transakcji" - GET /api/ledger returns 403 for anyone
 * else, which the backend enforces regardless of this page even being reachable. */
@Component({
  selector: 'app-global-ledger',
  imports: [FormsModule, MatIconModule, TranslatePipe, LoadingSpinner],
  templateUrl: './global-ledger.html',
  styleUrl: './global-ledger.scss',
})
export class GlobalLedger {
  private readonly ledgerApi = inject(LedgerApiService);
  private readonly translate = inject(TranslateService);

  readonly entries = signal<GlobalLedgerEntry[]>([]);
  readonly loading = signal(true);
  readonly forbidden = signal(false);

  // Filters: by child, by collection, by kind of event. All three combine.
  readonly studentFilter = signal('');
  readonly collectionFilter = signal('');
  readonly kind = signal<LedgerKind>('all');
  readonly kinds: LedgerKind[] = ['all', 'payments', 'piggy', 'settlements'];

  /** Children and collections that actually appear in the log, for the two dropdowns. */
  readonly studentOptions = computed(() => {
    const byId = new Map<string, string>();
    for (const e of this.entries()) {
      byId.set(e.studentId, e.studentName);
    }
    return [...byId].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name, 'pl'));
  });

  readonly collectionOptions = computed(() => {
    const byId = new Map<string, string>();
    for (const e of this.entries()) {
      if (e.params['collectionId']) {
        byId.set(e.params['collectionId'], e.params['collectionTitle'] ?? e.params['collectionId']);
      }
    }
    return [...byId].map(([id, title]) => ({ id, title }));
  });

  readonly filtered = computed(() => {
    const student = this.studentFilter();
    const collection = this.collectionFilter();
    const kind = this.kind();
    return this.entries().filter(
      (e) =>
        (!student || e.studentId === student) &&
        (!collection || e.params['collectionId'] === collection) &&
        (kind === 'all' || KIND_EVENTS[kind].includes(e.eventType)),
    );
  });

  readonly isFiltered = computed(() => !!this.studentFilter() || !!this.collectionFilter() || this.kind() !== 'all');

  clearFilters(): void {
    this.studentFilter.set('');
    this.collectionFilter.set('');
    this.kind.set('all');
  }

  /** Entries grouped by calendar day (already newest first from the backend), so a long feed
   *  reads like a diary rather than one undifferentiated list. */
  readonly days = computed(() => {
    const locale = localeFor(this.translate.currentLang());
    const groups: { label: string; entries: GlobalLedgerEntry[] }[] = [];
    for (const entry of this.filtered()) {
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

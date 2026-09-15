import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { TranslatePipe } from '@ngx-translate/core';
import { ParentApiService } from '../core/parent-api.service';
import { LedgerApiService } from '../core/ledger-api.service';
import { LedgerEntry, Parent } from '../core/models';

@Component({
  selector: 'app-parent-view',
  imports: [RouterLink, MatCardModule, MatListModule, TranslatePipe],
  templateUrl: './parent-view.html',
  styleUrl: './parent-view.scss',
})
export class ParentView {
  private readonly route = inject(ActivatedRoute);
  private readonly parentApi = inject(ParentApiService);
  private readonly ledgerApi = inject(LedgerApiService);

  readonly parent = signal<Parent | null>(null);
  readonly ledger = signal<LedgerEntry[]>([]);

  constructor() {
    const parentId = this.route.snapshot.paramMap.get('id')!;
    this.parentApi.get(parentId).subscribe((parent) => this.parent.set(parent));
    this.ledgerApi.getFor(parentId).subscribe((entries) => this.ledger.set(entries));
  }

  /** Translation key for one ledger entry - see LedgerEventType javadoc: the backend
   *  ships an enum + params, rendering the sentence is this frontend's job via i18n. */
  translationKeyFor(entry: LedgerEntry): string {
    return 'ledger.' + entry.eventType;
  }
}

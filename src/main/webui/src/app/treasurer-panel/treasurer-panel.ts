import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { TranslatePipe } from '@ngx-translate/core';
import { ParentApiService } from '../core/parent-api.service';
import { CollectionApiService } from '../core/collection-api.service';
import { Parent } from '../core/models';

@Component({
  selector: 'app-treasurer-panel',
  imports: [RouterLink, FormsModule, MatCardModule, MatListModule, MatFormFieldModule, MatInputModule, MatButtonModule, TranslatePipe],
  templateUrl: './treasurer-panel.html',
  styleUrl: './treasurer-panel.scss',
})
export class TreasurerPanel {
  private readonly parentApi = inject(ParentApiService);
  private readonly collectionApi = inject(CollectionApiService);

  readonly parents = signal<Parent[]>([]);
  readonly me = signal<Parent | null>(null);
  readonly bankAccountNumber = signal('');
  readonly blikPhoneNumber = signal('');
  readonly paymentInfoSaved = signal(false);

  readonly newParentFirstName = signal('');
  readonly newParentLastName = signal('');
  readonly newParentEmail = signal('');
  readonly newParentExpectedSenderName = signal('');

  readonly newCollectionTitle = signal('');
  readonly newCollectionDescription = signal('');
  readonly newCollectionBaseAmount = signal<number>(0);
  readonly collectionCreated = signal(false);

  constructor() {
    this.reloadParents();
    this.parentApi.me().subscribe((me) => {
      this.me.set(me);
      this.bankAccountNumber.set(me.bankAccountNumber ?? '');
      this.blikPhoneNumber.set(me.blikPhoneNumber ?? '');
    });
  }

  reloadParents(): void {
    this.parentApi.list().subscribe((parents) => this.parents.set(parents));
  }

  savePaymentInfo(): void {
    const currentUser = this.me();
    if (!currentUser) {
      return;
    }
    this.parentApi.updatePaymentInfo(currentUser.id, this.bankAccountNumber(), this.blikPhoneNumber()).subscribe((updated) => {
      this.me.set(updated);
      this.paymentInfoSaved.set(true);
    });
  }

  createParent(): void {
    this.parentApi
      .create(this.newParentFirstName(), this.newParentLastName(), this.newParentEmail(), this.newParentExpectedSenderName())
      .subscribe(() => {
        this.newParentFirstName.set('');
        this.newParentLastName.set('');
        this.newParentEmail.set('');
        this.newParentExpectedSenderName.set('');
        this.reloadParents();
      });
  }

  createCollection(): void {
    this.collectionApi
      .create(this.newCollectionTitle(), this.newCollectionDescription(), this.newCollectionBaseAmount())
      .subscribe(() => {
        this.newCollectionTitle.set('');
        this.newCollectionDescription.set('');
        this.newCollectionBaseAmount.set(0);
        this.collectionCreated.set(true);
      });
  }
}

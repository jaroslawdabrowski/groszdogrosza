import { Component, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ParentApiService } from '../core/parent-api.service';
import { CollectionApiService } from '../core/collection-api.service';
import { Parent } from '../core/models';

@Component({
  selector: 'app-treasurer-panel',
  imports: [
    RouterLink,
    FormsModule,
    MatCardModule,
    MatListModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    TranslatePipe,
  ],
  templateUrl: './treasurer-panel.html',
  styleUrl: './treasurer-panel.scss',
})
export class TreasurerPanel {
  private readonly parentApi = inject(ParentApiService);
  private readonly collectionApi = inject(CollectionApiService);
  private readonly translate = inject(TranslateService);

  readonly parents = signal<Parent[]>([]);
  readonly me = signal<Parent | null>(null);
  readonly bankAccountNumber = signal('');
  readonly blikPhoneNumber = signal('');
  readonly paymentInfoSaved = signal(false);

  /** Per-parent feedback for the account-creation/resend buttons, keyed by parent id - a
   *  translation key plus whether it's a success or error, rendered next to that row only. */
  readonly accountStatus = signal<Record<string, { kind: 'success' | 'error'; key: string } | undefined>>({});
  readonly accountActionInFlight = signal<Record<string, boolean>>({});

  /** Which parent's row is currently showing the inline edit form, if any - only one at a time. */
  readonly editingParentId = signal<string | null>(null);
  readonly editFirstName = signal('');
  readonly editLastName = signal('');
  readonly editEmail = signal('');
  readonly editExpectedSenderName = signal('');

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

  startEdit(parent: Parent): void {
    this.editingParentId.set(parent.id);
    this.editFirstName.set(parent.firstName);
    this.editLastName.set(parent.lastName);
    this.editEmail.set(parent.email);
    this.editExpectedSenderName.set(parent.expectedSenderName);
    this.accountStatus.update((s) => ({ ...s, [parent.id]: undefined }));
  }

  cancelEdit(): void {
    this.editingParentId.set(null);
  }

  isEditing(parentId: string): boolean {
    return this.editingParentId() === parentId;
  }

  saveEdit(parentId: string): void {
    this.parentApi.update(parentId, this.editFirstName(), this.editLastName(), this.editEmail(), this.editExpectedSenderName()).subscribe({
      next: () => {
        this.editingParentId.set(null);
        this.reloadParents();
      },
      error: () => {
        this.accountStatus.update((s) => ({ ...s, [parentId]: { kind: 'error', key: 'treasurer.editFailed' } }));
      },
    });
  }

  deleteParent(parent: Parent): void {
    const confirmed = window.confirm(
      this.translate.instant('treasurer.deleteConfirm', { name: `${parent.firstName} ${parent.lastName}` }),
    );
    if (!confirmed) {
      return;
    }
    this.parentApi.delete(parent.id).subscribe({
      next: () => this.reloadParents(),
      error: () => {
        this.accountStatus.update((s) => ({ ...s, [parent.id]: { kind: 'error', key: 'treasurer.deleteFailed' } }));
      },
    });
  }

  initialsFor(parent: Parent): string {
    return `${parent.firstName.charAt(0)}${parent.lastName.charAt(0)}`.toUpperCase();
  }

  isAccountActionInFlight(parentId: string): boolean {
    return this.accountActionInFlight()[parentId] === true;
  }

  accountStatusFor(parentId: string): { kind: 'success' | 'error'; key: string } | undefined {
    return this.accountStatus()[parentId];
  }

  createAccount(parent: Parent): void {
    this.runAccountAction(parent.id, this.parentApi.createCognitoAccount(parent.id), 'treasurer.accountCreated', (err) =>
      err.status === 409 ? 'treasurer.accountAlreadyExists' : 'treasurer.accountActionFailed',
    );
  }

  resendInvitation(parent: Parent): void {
    this.runAccountAction(parent.id, this.parentApi.resendCognitoInvitation(parent.id), 'treasurer.invitationResent', () =>
      'treasurer.accountActionFailed',
    );
  }

  private runAccountAction(
    parentId: string,
    request: Observable<void>,
    successKey: string,
    errorKeyFor: (err: { status: number }) => string,
  ): void {
    this.accountActionInFlight.update((s) => ({ ...s, [parentId]: true }));
    this.accountStatus.update((s) => ({ ...s, [parentId]: undefined }));
    request.subscribe({
      next: () => {
        this.accountActionInFlight.update((s) => ({ ...s, [parentId]: false }));
        this.accountStatus.update((s) => ({ ...s, [parentId]: { kind: 'success', key: successKey } }));
      },
      error: (err) => {
        this.accountActionInFlight.update((s) => ({ ...s, [parentId]: false }));
        this.accountStatus.update((s) => ({ ...s, [parentId]: { kind: 'error', key: errorKeyFor(err) } }));
      },
    });
  }
}

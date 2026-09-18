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
import { StudentApiService } from '../core/student-api.service';
import { CollectionApiService } from '../core/collection-api.service';
import { Parent, Student } from '../core/models';

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
  private readonly studentApi = inject(StudentApiService);
  private readonly collectionApi = inject(CollectionApiService);
  private readonly translate = inject(TranslateService);

  readonly students = signal<Student[]>([]);
  readonly me = signal<Parent | null>(null);
  readonly bankAccountNumber = signal('');
  readonly blikPhoneNumber = signal('');
  readonly paymentInfoSaved = signal(false);

  /** Per-parent feedback for the account-creation/resend/edit/delete actions, keyed by
   *  parent id - a translation key plus whether it's a success or error, rendered next to
   *  that row only. */
  readonly accountStatus = signal<Record<string, { kind: 'success' | 'error'; key: string } | undefined>>({});
  readonly accountActionInFlight = signal<Record<string, boolean>>({});

  /** Which parent's row is currently showing the inline edit form, if any - only one at a time. */
  readonly editingParentId = signal<string | null>(null);
  readonly editFirstName = signal('');
  readonly editLastName = signal('');
  readonly editEmail = signal('');
  readonly editExpectedSenderName = signal('');

  /** Which student's card is showing the inline "edit name" form. */
  readonly editingStudentId = signal<string | null>(null);
  readonly editStudentFirstName = signal('');
  readonly editStudentLastName = signal('');

  /** Which student's card currently has its "add parent" mini-form open (a student has at
   *  most 2 parent slots - see the backend Parent.studentId javadoc). */
  readonly addingParentToStudentId = signal<string | null>(null);
  readonly newParentFirstName = signal('');
  readonly newParentLastName = signal('');
  readonly newParentEmail = signal('');
  readonly newParentExpectedSenderName = signal('');

  readonly newStudentFirstName = signal('');
  readonly newStudentLastName = signal('');
  readonly studentCreated = signal(false);

  readonly newCollectionTitle = signal('');
  readonly newCollectionDescription = signal('');
  readonly newCollectionBaseAmount = signal<number>(0);
  readonly collectionCreated = signal(false);

  constructor() {
    this.reloadStudents();
    this.parentApi.me().subscribe((me) => {
      this.me.set(me);
      this.bankAccountNumber.set(me.bankAccountNumber ?? '');
      this.blikPhoneNumber.set(me.blikPhoneNumber ?? '');
    });
  }

  reloadStudents(): void {
    this.studentApi.list().subscribe((students) => this.students.set(students));
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

  createStudent(): void {
    this.studentApi.create(this.newStudentFirstName(), this.newStudentLastName()).subscribe(() => {
      this.newStudentFirstName.set('');
      this.newStudentLastName.set('');
      this.studentCreated.set(true);
      this.reloadStudents();
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

  // --- student name edit ---

  startEditStudent(student: Student): void {
    this.editingStudentId.set(student.id);
    this.editStudentFirstName.set(student.firstName);
    this.editStudentLastName.set(student.lastName);
  }

  cancelEditStudent(): void {
    this.editingStudentId.set(null);
  }

  isEditingStudent(studentId: string): boolean {
    return this.editingStudentId() === studentId;
  }

  saveEditStudent(studentId: string): void {
    this.studentApi.update(studentId, this.editStudentFirstName(), this.editStudentLastName()).subscribe({
      next: () => {
        this.editingStudentId.set(null);
        this.reloadStudents();
      },
      error: () => this.setAccountStatus(studentId, 'error', 'treasurer.editFailed'),
    });
  }

  /** A student whose parent list includes the currently logged-in treasurer - deleting it
   *  would delete the treasurer's own account, so the delete button is disabled for it. */
  isOwnStudent(student: Student): boolean {
    const myId = this.me()?.id;
    return myId != null && student.parents.some((p) => p.id === myId);
  }

  deleteStudent(student: Student): void {
    const confirmed = window.confirm(
      this.translate.instant('treasurer.deleteStudentConfirm', { name: `${student.firstName} ${student.lastName}` }),
    );
    if (!confirmed) {
      return;
    }
    this.studentApi.delete(student.id).subscribe({
      next: () => this.reloadStudents(),
      error: () => this.setAccountStatus(student.id, 'error', 'treasurer.deleteFailed'),
    });
  }

  // --- add parent to a student ---

  startAddParent(studentId: string): void {
    this.addingParentToStudentId.set(studentId);
    this.newParentFirstName.set('');
    this.newParentLastName.set('');
    this.newParentEmail.set('');
    this.newParentExpectedSenderName.set('');
  }

  cancelAddParent(): void {
    this.addingParentToStudentId.set(null);
  }

  isAddingParentTo(studentId: string): boolean {
    return this.addingParentToStudentId() === studentId;
  }

  addParent(studentId: string): void {
    this.studentApi
      .addParent(studentId, this.newParentFirstName(), this.newParentLastName(), this.newParentEmail(), this.newParentExpectedSenderName())
      .subscribe({
        next: () => {
          this.addingParentToStudentId.set(null);
          this.reloadStudents();
        },
        error: (err) =>
          this.setAccountStatus(studentId, 'error', err.status === 409 ? 'treasurer.tooManyParents' : 'treasurer.editFailed'),
      });
  }

  // --- parent edit/delete ---

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
        this.reloadStudents();
      },
      error: () => this.setAccountStatus(parentId, 'error', 'treasurer.editFailed'),
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
      next: () => this.reloadStudents(),
      error: () => this.setAccountStatus(parent.id, 'error', 'treasurer.deleteFailed'),
    });
  }

  initialsFor(person: { firstName: string; lastName: string }): string {
    return `${person.firstName.charAt(0)}${person.lastName.charAt(0)}`.toUpperCase();
  }

  isAccountActionInFlight(parentId: string): boolean {
    return this.accountActionInFlight()[parentId] === true;
  }

  accountStatusFor(id: string): { kind: 'success' | 'error'; key: string } | undefined {
    return this.accountStatus()[id];
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

  private setAccountStatus(id: string, kind: 'success' | 'error', key: string): void {
    this.accountStatus.update((s) => ({ ...s, [id]: { kind, key } }));
  }
}

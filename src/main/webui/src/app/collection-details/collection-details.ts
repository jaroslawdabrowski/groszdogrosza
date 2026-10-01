import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CollectionApiService } from '../core/collection-api.service';
import { StudentApiService } from '../core/student-api.service';
import { CurrentUserService } from '../core/current-user.service';
import { ALLOWED_ATTACHMENT_TYPES, AttachmentApiService, MAX_ATTACHMENT_SIZE_BYTES } from '../core/attachment-api.service';
import {
  Attachment,
  CollectionDetails as CollectionDetailsModel,
  CollectionProgress,
  SettlementResult,
  Student,
  isCollectionDetails,
} from '../core/models';
import { LoadingSpinner } from '../shared/loading-spinner/loading-spinner';
import { MoneyPipe } from '../shared/money.pipe';
import { RosterDots } from '../shared/roster-dots/roster-dots';

/** The roster filter above the student list - see CollectionDetails.filteredRows. */
type RosterFilter = 'all' | 'owe' | 'paid';

/** One row of the requirements table - either a student genuinely included in the
 *  collection (backed by a real ContributionRequirement) or one who isn't (backed by
 *  nothing - shown greyed out with an "add back" action instead of the usual
 *  required/paid/status columns, which don't apply to them at all). Only ever built while
 *  the collection is ACTIVE - see CollectionDetails.rosterRows. */
interface RosterRow {
  studentId: string;
  studentName: string;
  included: boolean;
  requiredAmount: number;
  paidAmount: number;
  status: string;
}

/**
 * Renders one of two shapes depending on the caller's role, as returned by the backend
 * (see CollectionResource.get / CollectionProgressResponse): a treasurer sees the full
 * per-student requirement/contribution breakdown and the settle form, a regular parent sees
 * aggregate progress only.
 */
@Component({
  selector: 'app-collection-details',
  imports: [RouterLink, FormsModule, MatIconModule, TranslatePipe, LoadingSpinner, MoneyPipe, RosterDots],
  templateUrl: './collection-details.html',
  styleUrl: './collection-details.scss',
})
export class CollectionDetails {
  private readonly route = inject(ActivatedRoute);
  private readonly collectionApi = inject(CollectionApiService);
  private readonly studentApi = inject(StudentApiService);
  private readonly attachmentApi = inject(AttachmentApiService);
  private readonly translate = inject(TranslateService);
  private readonly currentUser = inject(CurrentUserService);

  readonly view = signal<CollectionDetailsModel | CollectionProgress | null>(null);
  /** The full class roster - only ever fetched for a treasurer (StudentResource.list is
   *  treasurer-only). Used to show students NOT in this collection as greyed-out rows
   *  alongside the ones that are - see rosterRows. */
  readonly allStudents = signal<Student[]>([]);
  readonly attachments = signal<Attachment[]>([]);
  readonly uploadingAttachment = signal(false);
  readonly attachmentError = signal<string | null>(null);
  readonly allowedAttachmentTypes = ALLOWED_ATTACHMENT_TYPES.join(',');
  /** Only guards the FIRST load (a Lambda cold start can take real seconds - see
   *  LoadingSpinner's javadoc) - never set back to true, so a settle()/removeStudent()
   *  triggered reload() doesn't flash the whole page back to a spinner; `view()` already
   *  holds the previous value while that reload is in flight. */
  readonly loading = signal(true);
  readonly settlementPreview = signal<SettlementResult | null>(null);
  readonly actualCostSpent = signal<number>(0);
  readonly filter = signal<RosterFilter>('all');
  readonly filters: RosterFilter[] = ['all', 'owe', 'paid'];
  readonly isCollectionDetails = isCollectionDetails;

  private readonly collectionId: string;

  constructor() {
    this.collectionId = this.route.snapshot.paramMap.get('id')!;
    this.reload();
    this.reloadAttachments();
  }

  reload(): void {
    this.collectionApi.get(this.collectionId).subscribe({
      next: (view) => {
        this.view.set(view);
        this.loading.set(false);
        if (isCollectionDetails(view)) {
          // Pre-filled with what's actually been collected so far, not left at 0 - the
          // treasurer almost always wants to settle at exactly that amount, and can still
          // edit it if the real invoice/cost differs.
          this.actualCostSpent.set(this.totalCollected());
          this.studentApi.list().subscribe((students) => this.allStudents.set(students));
        }
      },
      error: () => this.loading.set(false),
    });
  }

  /** Every student in the class, merged with this collection's own requirements - a student
   *  with no requirement (never included, or removed earlier) still shows up here, greyed
   *  out - with an "add back" action while ACTIVE, and as a plain record once SETTLED (the
   *  add/remove buttons only exist while ACTIVE). Nothing records
   *  who was left off at creation time, so for a SETTLED collection this compares against
   *  TODAY's roster: a student who joined the class after it settled also shows as not
   *  included - an accepted approximation, confirmed with the treasurer.
   *
   *  A `computed`, not a plain method: a method called from the template returns brand-new
   *  row objects on every change-detection tick, which once made the rows rebuild between a
   *  button's mousedown and its click, so the click never fired. */
  readonly rosterRows = computed<RosterRow[]>(() => {
    const current = this.view();
    if (!current || !isCollectionDetails(current)) {
      return [];
    }
    const included: RosterRow[] = current.requirements.map((r) => ({
      studentId: r.studentId,
      studentName: r.studentName,
      included: true,
      requiredAmount: r.requiredAmount,
      paidAmount: r.paidAmount,
      status: r.status,
    }));
    const includedIds = new Set(included.map((r) => r.studentId));
    const excluded: RosterRow[] = this.allStudents()
      .filter((s) => !includedIds.has(s.id))
      .map((s) => ({
        studentId: s.id,
        studentName: `${s.firstName} ${s.lastName}`,
        included: false,
        requiredAmount: 0,
        paidAmount: 0,
        status: '',
      }));
    return [...included, ...excluded];
  });

  /** "Do zapłaty" = included and still PENDING; "Zapłacone" = included and settled up.
   *  Students outside the collection only show under "Wszyscy". */
  readonly filteredRows = computed<RosterRow[]>(() => {
    const rows = this.rosterRows();
    switch (this.filter()) {
      case 'owe':
        return rows.filter((r) => r.included && r.status === 'PENDING');
      case 'paid':
        return rows.filter((r) => r.included && r.status !== 'PENDING');
      default:
        return rows;
    }
  });

  filterCount(filter: RosterFilter): number {
    const rows = this.rosterRows();
    switch (filter) {
      case 'owe':
        return rows.filter((r) => r.included && r.status === 'PENDING').length;
      case 'paid':
        return rows.filter((r) => r.included && r.status !== 'PENDING').length;
      default:
        return rows.length;
    }
  }

  /** Included students who paid part of what they owe - the half-filled roster dots. */
  partlyPaidStudentsCount(): number {
    return this.rosterRows().filter((r) => r.included && r.status === 'PENDING' && r.paidAmount > 0).length;
  }

  excludedStudentsCount(): number {
    return this.rosterRows().filter((r) => !r.included).length;
  }

  initials(name: string): string {
    return name
      .split(' ')
      .filter((part) => part.length > 0)
      .slice(0, 2)
      .map((part) => part[0].toUpperCase())
      .join('');
  }

  /** Avatar/pill tone for one roster row. */
  tone(row: RosterRow): string {
    if (!row.included) {
      return '';
    }
    return row.status === 'PENDING' ? 'owe' : 'ok';
  }

  /** Puts a student back into the collection - see backend AddStudentToCollectionUseCase:
   *  immediately sweeps in whatever their current piggy bank balance covers, exactly like
   *  when the collection was first created, with a real ledger entry either way. */
  addStudent(studentId: string): void {
    this.collectionApi.addStudent(this.collectionId, studentId).subscribe(() => this.reload());
  }

  reloadAttachments(): void {
    this.attachmentApi.list(this.collectionId).subscribe((attachments) => this.attachments.set(attachments));
  }

  /** Client-side validation is purely a fast/friendly rejection - see AttachmentPolicy on
   *  the backend for the actual enforcement point, which a malicious client could still
   *  bypass by calling the API directly. */
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.attachmentError.set(null);
    if (!ALLOWED_ATTACHMENT_TYPES.includes(file.type)) {
      this.attachmentError.set(this.translate.instant('collectionDetails.attachmentTypeError'));
      return;
    }
    if (file.size > MAX_ATTACHMENT_SIZE_BYTES) {
      this.attachmentError.set(this.translate.instant('collectionDetails.attachmentSizeError'));
      return;
    }

    this.uploadingAttachment.set(true);
    this.attachmentApi.upload(this.collectionId, file).subscribe({
      next: () => {
        this.uploadingAttachment.set(false);
        this.reloadAttachments();
      },
      error: () => {
        this.uploadingAttachment.set(false);
        this.attachmentError.set(this.translate.instant('collectionDetails.attachmentUploadFailed'));
      },
    });
  }

  deleteAttachment(attachmentId: string, fileName: string): void {
    const confirmed = window.confirm(this.translate.instant('collectionDetails.deleteAttachmentConfirm', { name: fileName }));
    if (!confirmed) {
      return;
    }
    this.attachmentApi.delete(this.collectionId, attachmentId).subscribe(() => this.reloadAttachments());
  }

  isImageAttachment(contentType: string): boolean {
    return contentType.startsWith('image/');
  }

  /** Settling is final (the collection becomes SETTLED and any surplus goes to piggy banks
   *  for good), so it asks first, restating the numbers being committed. */
  settle(): void {
    const confirmed = window.confirm(
      this.translate.instant('collectionDetails.settleConfirm', {
        cost: this.actualCostSpent(),
        collected: this.totalCollected(),
      }),
    );
    if (!confirmed) {
      return;
    }
    this.collectionApi.settle(this.collectionId, this.actualCostSpent()).subscribe((result) => {
      this.settlementPreview.set(result);
      this.reload();
    });
  }

  /** Only meaningful while the collection is ACTIVE - see backend
   *  RemoveStudentFromCollectionUseCase, which refunds whatever the student already paid
   *  back to their piggy bank. */
  removeStudent(studentId: string, studentName: string): void {
    const confirmed = window.confirm(this.translate.instant('collectionDetails.removeStudentConfirm', { name: studentName }));
    if (!confirmed) {
      return;
    }
    this.collectionApi.removeStudent(this.collectionId, studentId).subscribe(() => this.reload());
  }

  /** The logged-in parent's own linked child - null until CurrentUserService's own load
   *  (triggered once, from App) resolves, or if this account has no linked Student yet. Used
   *  to show a self-service opt-out/opt-in button on the non-treasurer progress card below -
   *  a parent needing to pull their own sick/departing child out of an ACTIVE collection (or
   *  put them back in) without going through the treasurer (see backend
   *  AuthorizationSupport.requireSelfOrTreasurerForStudent, which this same button relies on
   *  server-side, not just this null check). */
  myStudentId(): string | null {
    return this.currentUser.studentId();
  }

  /** Mirrors removeStudent, but for the caller's own child specifically - different
   *  confirmation copy (no "this family" framing, since the treasurer's audience for that
   *  message doesn't apply here) and no studentName param, since the parent already knows
   *  whose collection membership they're changing. */
  removeMyStudent(): void {
    const studentId = this.myStudentId();
    if (!studentId) {
      return;
    }
    const confirmed = window.confirm(this.translate.instant('collectionDetails.removeMyStudentConfirm'));
    if (!confirmed) {
      return;
    }
    this.collectionApi.removeStudent(this.collectionId, studentId).subscribe(() => this.reload());
  }

  addMyStudent(): void {
    const studentId = this.myStudentId();
    if (!studentId) {
      return;
    }
    this.collectionApi.addStudent(this.collectionId, studentId).subscribe(() => this.reload());
  }

  /** SettlementResult only carries studentId (see backend SettlementResultResponse) - name
   *  it against whatever the last-loaded requirements breakdown knows, so the settlement
   *  card can show a name instead of a raw id. */
  nameForStudent(studentId: string): string {
    const current = this.view();
    if (current && isCollectionDetails(current)) {
      const match = current.requirements.find((r) => r.studentId === studentId);
      if (match) {
        return match.studentName;
      }
    }
    return studentId;
  }

  /** How many included students' requirements are no longer PENDING (paid in full or
   *  overpaid), out of how many are included in total - shown on the settle card so the
   *  treasurer can see at a glance who's still outstanding before committing to a final
   *  cost. Status-based, not amount-based, matching CollectionProgressResponse's own
   *  studentsPaidCount on the backend. */
  paidStudentsCount(): number {
    const current = this.view();
    if (!current || !isCollectionDetails(current)) {
      return 0;
    }
    return current.requirements.filter((r) => r.status !== 'PENDING').length;
  }

  totalStudentsCount(): number {
    const current = this.view();
    if (!current || !isCollectionDetails(current)) {
      return 0;
    }
    return current.requirements.length;
  }

  /** How many students were opted out of this collection before it settled - see backend
   *  CollectionDetailsResponse.removedStudentsCount's javadoc for why this can't be derived
   *  from `requirements`/`contributions` (a removal deletes them outright) and has to come
   *  from the backend's own ledger-derived count instead. */
  removedStudentsCount(): number {
    const current = this.view();
    if (!current || !isCollectionDetails(current)) {
      return 0;
    }
    return current.removedStudentsCount;
  }

  /** The nominal full value of the collection (every included student's base amount, before
   *  any piggy-bank discount) - what the treasurer was originally asking for in total,
   *  regardless of how much of it ended up pre-covered from savings. */
  totalExpected(): number {
    const current = this.view();
    if (!current || !isCollectionDetails(current)) {
      return 0;
    }
    return current.collection.baseAmountPerStudent * current.requirements.length;
  }

  /** Money actually received for this collection - the sum of every recorded Contribution,
   *  not the requirements' paidAmount (which a settled collection can leave at a value that
   *  no longer matches what's actually in hand after leftovers are swept back out). This is
   *  the number a treasurer would want on a report handed over with cash/a transfer receipt
   *  - see the print report below. */
  totalCollected(): number {
    const current = this.view();
    if (!current || !isCollectionDetails(current)) {
      return 0;
    }
    return current.contributions.reduce((sum, c) => sum + c.amount, 0);
  }

  /** Only meaningful the moment it's called (not stored as state) - the print report always
   *  shows "printed on <today>", so it's fine to compute fresh on each render rather than
   *  fixing it once. Formatted in the app's currently-chosen language, not the browser's own
   *  locale - the rest of the printed page (labels, statuses) is already in that language,
   *  and this report is meant to be handed to someone else (a teacher), so it should read
   *  consistently regardless of what locale the treasurer's own browser happens to be set to. */
  printedOnLabel(): string {
    const locale = this.translate.currentLang() === 'en' ? 'en-US' : 'pl-PL';
    return new Date().toLocaleDateString(locale, { year: 'numeric', month: 'long', day: 'numeric' });
  }

  print(): void {
    window.print();
  }
}

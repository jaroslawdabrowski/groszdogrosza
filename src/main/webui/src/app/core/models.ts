/** `myStudentStatus` is the CALLER's OWN child's status in this collection - never another
 *  family's (see backend CollectionResponse's javadoc): one of ContributionRequirement's
 *  statuses if the child is included, `'NOT_INCLUDED'` if the collection exists but the
 *  child isn't in it, or `null`/undefined if there's no logged-in parent (or no linked
 *  Student) to resolve "own child" for in the first place - see MyStudentStatusBadge, which
 *  renders nothing in that last case. */
export type MyStudentStatus = 'NOT_INCLUDED' | 'PENDING' | 'PAID' | 'OVERPAID';

export interface CollectionSummary {
  id: string;
  title: string;
  description: string;
  status: 'DRAFT' | 'ACTIVE' | 'SETTLED';
  baseAmountPerStudent: number;
  createdAt: string;
  myStudentStatus?: MyStudentStatus | null;
}

export interface RequirementView {
  studentId: string;
  studentName: string;
  requiredAmount: number;
  paidAmount: number;
  status: 'PENDING' | 'PAID' | 'OVERPAID';
}

export interface ContributionView {
  id: string;
  studentId: string;
  studentName: string;
  amount: number;
  source: 'BANK_STATEMENT_AUTO' | 'MANUAL' | 'PIGGY_BANK_APPLIED';
  receivedAt: string;
}

export interface CollectionDetails {
  collection: CollectionSummary;
  requirements: RequirementView[];
  contributions: ContributionView[];
  /** How many students were removed from this collection before it settled (see backend
   *  CollectionDetailsResponse's javadoc) - their requirement/contributions are deleted
   *  outright, so this is the only remaining trace, resolved from the ledger. */
  removedStudentsCount: number;
}

/**
 * What GET /api/collections/{id} returns to a non-treasurer parent instead of
 * CollectionDetails - aggregate progress only, never the per-student breakdown (see
 * backend CollectionProgressResponse for why). Distinguish the two response shapes with
 * `isCollectionDetails` below rather than a discriminator field, since the backend just
 * returns whichever DTO fits the caller's role.
 */
export interface CollectionProgress {
  collection: CollectionSummary;
  studentsCount: number;
  studentsPaidCount: number;
  totalRequired: number;
  totalPaid: number;
  percentComplete: number;
}

export function isCollectionDetails(value: CollectionDetails | CollectionProgress): value is CollectionDetails {
  return (value as CollectionDetails).requirements !== undefined;
}

export interface StudentSettlement {
  studentId: string;
  amountPaid: number;
  leftoverToCredit: number;
}

export interface SettlementResult {
  totalContributed: number;
  actualCostSpent: number;
  totalSurplus: number;
  totalShortfall: number;
  studentSettlements: StudentSettlement[];
}

export interface Parent {
  id: string;
  studentId: string | null;
  firstName: string;
  lastName: string;
  email: string;
  expectedSenderName: string;
  role: 'TREASURER' | 'PARENT';
  bankAccountNumber: string | null;
  blikPhoneNumber: string | null;
}

/** A student has 0-2 parents - see the backend Parent.studentId javadoc for why the cap. */
export interface Student {
  id: string;
  firstName: string;
  lastName: string;
  piggyBankBalance: number;
  parents: Parent[];
}

export interface LedgerEntry {
  id: string;
  eventType: 'CONTRIBUTION_RECEIVED' | 'COLLECTION_SETTLED' | 'PIGGY_BANK_CREDITED' | 'PIGGY_BANK_APPLIED_TO_COLLECTION';
  occurredAt: string;
  params: Record<string, string>;
}

/** Same as LedgerEntry, plus studentName - see backend GlobalLedgerEntryResponse. */
export interface GlobalLedgerEntry extends LedgerEntry {
  studentId: string;
  studentName: string;
}

export interface PublicPaymentInfo {
  bankAccountNumber: string | null;
  blikPhoneNumber: string | null;
}

/** GET /api/public/overview - unauthenticated. See backend PublicOverviewResponse. */
export interface PublicOverview {
  paymentInfo: PublicPaymentInfo | null;
  activeCollections: CollectionProgress[];
}

/** A photo/receipt attached to a collection to document what the money was spent on - see
 *  backend CollectionAttachmentResource. `viewUrl` is a freshly-minted, short-lived
 *  presigned S3 URL, not a durable link - always re-fetch the attachment list rather than
 *  caching one of these across page loads. */
export interface Attachment {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
  viewUrl: string;
}

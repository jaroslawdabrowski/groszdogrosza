export interface CollectionSummary {
  id: string;
  title: string;
  description: string;
  status: 'DRAFT' | 'ACTIVE' | 'SETTLED';
  baseAmountPerParent: number;
  createdAt: string;
}

export interface RequirementView {
  parentId: string;
  requiredAmount: number;
  paidAmount: number;
  status: 'PENDING' | 'PAID' | 'OVERPAID';
}

export interface ContributionView {
  id: string;
  parentId: string;
  amount: number;
  source: 'BANK_STATEMENT_AUTO' | 'MANUAL' | 'PIGGY_BANK_APPLIED';
  receivedAt: string;
}

export interface CollectionDetails {
  collection: CollectionSummary;
  requirements: RequirementView[];
  contributions: ContributionView[];
}

/**
 * What GET /api/collections/{id} returns to a non-treasurer parent instead of
 * CollectionDetails - aggregate progress only, never the per-parent breakdown (see
 * backend CollectionProgressResponse for why). Distinguish the two response shapes with
 * `isCollectionDetails` below rather than a discriminator field, since the backend just
 * returns whichever DTO fits the caller's role.
 */
export interface CollectionProgress {
  collection: CollectionSummary;
  parentsCount: number;
  parentsPaidCount: number;
  totalRequired: number;
  totalPaid: number;
  percentComplete: number;
}

export function isCollectionDetails(value: CollectionDetails | CollectionProgress): value is CollectionDetails {
  return (value as CollectionDetails).requirements !== undefined;
}

export interface ParentSettlement {
  parentId: string;
  amountPaid: number;
  leftoverToCredit: number;
}

export interface SettlementResult {
  totalContributed: number;
  actualCostSpent: number;
  totalSurplus: number;
  totalShortfall: number;
  parentSettlements: ParentSettlement[];
}

export interface Parent {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  expectedSenderName: string;
  role: 'TREASURER' | 'PARENT';
  piggyBankBalance: number;
}

export interface LedgerEntry {
  id: string;
  eventType: 'CONTRIBUTION_RECEIVED' | 'COLLECTION_SETTLED' | 'PIGGY_BANK_CREDITED' | 'PIGGY_BANK_APPLIED_TO_COLLECTION';
  occurredAt: string;
  params: Record<string, string>;
}

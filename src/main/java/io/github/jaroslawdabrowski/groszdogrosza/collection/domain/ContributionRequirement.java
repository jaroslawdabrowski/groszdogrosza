package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

import java.math.BigDecimal;

/**
 * How much one specific student's family actually owes for one specific collection, and how
 * much of that they've paid so far. {@code requiredAmount} is the collection's nominal
 * {@code Collection.baseAmountPerStudent}, fixed at creation time and never recomputed later
 * (if the student's piggy bank balance changes afterwards for an unrelated reason, it does
 * not retroactively change what THIS collection asks of them). {@link #outstandingAmount()}
 * ({@code requiredAmount - paidAmount}) is what's actually still owed "at a glance" - it
 * starts below {@code requiredAmount} whenever the student already had some piggy bank
 * balance at creation time, because {@code CollectionService.createCollection} immediately
 * sweeps whatever it can cover into a real {@code Contribution} (see that method's own
 * comment) exactly like a genuine incoming payment would, rather than just discounting the
 * requirement and leaving no money movement or audit trail behind. This is deliberately the
 * SAME mechanism a real bank transfer uses ({@code ContributionAllocationPolicy}), not a
 * separate "silent" path - so a student whose entire share came from an already-full piggy
 * bank is a genuine contributor for settlement purposes too, not a bookkeeping fiction.
 */
public record ContributionRequirement(
        String id,
        String collectionId,
        String studentId,
        BigDecimal requiredAmount,
        BigDecimal paidAmount,
        ContributionRequirementStatus status) {

    public ContributionRequirement {
        if (requiredAmount.signum() < 0) {
            throw new IllegalArgumentException("requiredAmount cannot be negative: " + requiredAmount);
        }
        if (paidAmount.signum() < 0) {
            throw new IllegalArgumentException("paidAmount cannot be negative: " + paidAmount);
        }
    }

    public BigDecimal outstandingAmount() {
        BigDecimal outstanding = requiredAmount.subtract(paidAmount);
        return outstanding.signum() < 0 ? BigDecimal.ZERO : outstanding;
    }

    public ContributionRequirement withAdditionalPayment(BigDecimal additionalAmount) {
        BigDecimal newPaidAmount = paidAmount.add(additionalAmount);
        ContributionRequirementStatus newStatus;
        int comparison = newPaidAmount.compareTo(requiredAmount);
        if (comparison < 0) {
            newStatus = ContributionRequirementStatus.PENDING;
        } else if (comparison == 0) {
            newStatus = ContributionRequirementStatus.PAID;
        } else {
            newStatus = ContributionRequirementStatus.OVERPAID;
        }
        return new ContributionRequirement(id, collectionId, studentId, requiredAmount, newPaidAmount, newStatus);
    }
}

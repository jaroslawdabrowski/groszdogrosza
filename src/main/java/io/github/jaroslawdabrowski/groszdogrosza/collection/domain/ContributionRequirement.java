package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

import java.math.BigDecimal;

/**
 * How much one specific parent actually owes for one specific collection, and how much of
 * that they've paid so far. {@code requiredAmount} is {@code Collection.baseAmountPerParent}
 * minus whatever piggy bank balance that parent had available at the moment the
 * collection was activated (floored at zero - a parent's piggy bank can cover a
 * collection entirely, but a requirement is never negative). Fixed at creation time on
 * purpose: if the parent's piggy bank balance changes later (e.g. from an unrelated
 * transfer), it does not retroactively change what THIS collection asks of them - it
 * simply gets applied via {@code ContributionAllocationPolicy} the next time money comes in.
 */
public record ContributionRequirement(
        String id,
        String collectionId,
        String parentId,
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
        return new ContributionRequirement(id, collectionId, parentId, requiredAmount, newPaidAmount, newStatus);
    }
}

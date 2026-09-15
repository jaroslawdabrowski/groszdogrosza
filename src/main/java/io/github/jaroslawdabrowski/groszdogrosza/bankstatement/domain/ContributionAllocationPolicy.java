package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure decision logic for what happens to money once it's been matched to a parent: it
 * lands in the piggy bank first, then as much as needed sweeps out of the piggy bank to
 * cover that parent's outstanding requirements on currently ACTIVE collections, in the
 * order given (the caller - {@code bankstatement.application.BankStatementProcessingService}
 * - is expected to pass requirements ordered oldest-collection-first, so a parent's money
 * covers what they owe on the collection that's been open longest before a newer one).
 *
 * <p>No framework dependency, no I/O - testable with plain JUnit. This is the same "landing
 * spot" idea for both an actual incoming bank transfer AND, conceptually, the moment a
 * collection is created and immediately consumes existing piggy bank balance - but that
 * second case is simple enough (one requirement, one balance) that
 * {@code CollectionService.createCollection} computes it inline rather than via this policy.
 * This policy exists for the general N-requirements case a live incoming payment can hit.
 */
public final class ContributionAllocationPolicy {

    private ContributionAllocationPolicy() {
    }

    public static AllocationResult allocate(
            BigDecimal incomingAmount, BigDecimal currentPiggyBankBalance,
            List<ContributionRequirement> activePendingRequirementsOldestFirst) {
        if (incomingAmount.signum() < 0) {
            throw new IllegalArgumentException("incomingAmount cannot be negative: " + incomingAmount);
        }

        BigDecimal available = currentPiggyBankBalance.add(incomingAmount);
        List<RequirementAllocation> allocations = new ArrayList<>();

        for (ContributionRequirement requirement : activePendingRequirementsOldestFirst) {
            if (available.signum() <= 0) {
                break;
            }
            BigDecimal outstanding = requirement.outstandingAmount();
            if (outstanding.signum() <= 0) {
                continue;
            }
            BigDecimal toApply = available.min(outstanding);
            allocations.add(new RequirementAllocation(requirement.collectionId(), requirement.id(), toApply));
            available = available.subtract(toApply);
        }

        return new AllocationResult(available, List.copyOf(allocations));
    }

    public record AllocationResult(BigDecimal endingPiggyBankBalance, List<RequirementAllocation> allocations) {
    }

    public record RequirementAllocation(String collectionId, String requirementId, BigDecimal amountApplied) {
    }
}

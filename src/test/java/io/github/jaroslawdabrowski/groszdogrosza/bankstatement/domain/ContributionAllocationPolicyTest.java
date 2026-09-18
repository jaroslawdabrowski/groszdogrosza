package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain.ContributionAllocationPolicy.AllocationResult;
import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain.ContributionAllocationPolicy.RequirementAllocation;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirementStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContributionAllocationPolicyTest {

    private static ContributionRequirement requirement(String collectionId, BigDecimal required, BigDecimal paid) {
        return new ContributionRequirement("req-" + collectionId, collectionId, "student-1", required, paid,
                ContributionRequirementStatus.PENDING);
    }

    @Test
    void noActiveRequirementsMeansEverythingStaysInPiggyBank() {
        AllocationResult result = ContributionAllocationPolicy.allocate(
                new BigDecimal("30.00"), new BigDecimal("10.00"), List.of());

        assertEquals(new BigDecimal("40.00"), result.endingPiggyBankBalance());
        assertTrue(result.allocations().isEmpty());
    }

    @Test
    void fullyPaysASingleRequirementAndKeepsRemainderInPiggyBank() {
        List<ContributionRequirement> requirements = List.of(
                requirement("c1", new BigDecimal("50.00"), BigDecimal.ZERO));

        AllocationResult result = ContributionAllocationPolicy.allocate(
                new BigDecimal("70.00"), BigDecimal.ZERO, requirements);

        assertEquals(new BigDecimal("20.00"), result.endingPiggyBankBalance());
        assertEquals(1, result.allocations().size());
        RequirementAllocation allocation = result.allocations().get(0);
        assertEquals("c1", allocation.collectionId());
        assertEquals(new BigDecimal("50.00"), allocation.amountApplied());
    }

    @Test
    void partiallyPaysWhenNotEnoughToCoverTheWholeRequirement() {
        List<ContributionRequirement> requirements = List.of(
                requirement("c1", new BigDecimal("50.00"), BigDecimal.ZERO));

        AllocationResult result = ContributionAllocationPolicy.allocate(
                new BigDecimal("20.00"), BigDecimal.ZERO, requirements);

        assertEquals(new BigDecimal("0.00"), result.endingPiggyBankBalance());
        assertEquals(new BigDecimal("20.00"), result.allocations().get(0).amountApplied());
    }

    @Test
    void spreadsAcrossMultipleRequirementsOldestFirst() {
        List<ContributionRequirement> requirements = List.of(
                requirement("c1-oldest", new BigDecimal("30.00"), BigDecimal.ZERO),
                requirement("c2-newer", new BigDecimal("30.00"), BigDecimal.ZERO));

        AllocationResult result = ContributionAllocationPolicy.allocate(
                new BigDecimal("40.00"), BigDecimal.ZERO, requirements);

        assertEquals(2, result.allocations().size());
        assertEquals("c1-oldest", result.allocations().get(0).collectionId());
        assertEquals(new BigDecimal("30.00"), result.allocations().get(0).amountApplied());
        assertEquals("c2-newer", result.allocations().get(1).collectionId());
        assertEquals(new BigDecimal("10.00"), result.allocations().get(1).amountApplied());
        assertEquals(new BigDecimal("0.00"), result.endingPiggyBankBalance());
    }

    @Test
    void alreadyPartiallyPaidRequirementOnlyNeedsTheOutstandingPart() {
        List<ContributionRequirement> requirements = List.of(
                requirement("c1", new BigDecimal("50.00"), new BigDecimal("20.00")));

        AllocationResult result = ContributionAllocationPolicy.allocate(
                new BigDecimal("100.00"), BigDecimal.ZERO, requirements);

        assertEquals(new BigDecimal("30.00"), result.allocations().get(0).amountApplied());
        assertEquals(new BigDecimal("70.00"), result.endingPiggyBankBalance());
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.CollectionStatus;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirementStatus;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase.CollectionDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression test for a real production bug (2026-09-23): a collection where most students'
 * piggy banks already fully covered their share at creation time showed "0 zł / 24 zł, 0%"
 * even though 13 of 16 students were genuinely already covered. The root cause (and its real
 * fix) is in {@code CollectionService.createCollection} - it now sweeps a pre-existing piggy
 * bank balance into a real {@code Contribution} at creation, exactly like an incoming bank
 * transfer would, instead of only discounting {@code requiredAmount} with no corresponding
 * {@code paidAmount}. This class's plain summation is correct once that fix is in place - see
 * both classes' javadoc.
 */
class CollectionProgressResponseTest {

    private static Collection collection(String baseAmount) {
        return new Collection("c1", "Title", "Description", CollectionStatus.ACTIVE, new BigDecimal(baseAmount), Instant.now());
    }

    private static ContributionRequirement requirement(
            String studentId, String required, String paid, ContributionRequirementStatus status) {
        return new ContributionRequirement("r-" + studentId, "c1", studentId, new BigDecimal(required), new BigDecimal(paid), status);
    }

    @Test
    void aStudentFullyCoveredByAPreexistingPiggyBankBalanceCountsAsFullyPaidInTheTotals() {
        // 16 students, base amount 8 zł each - 13 already had >= 8 zł saved, swept into a
        // real Contribution at creation (requiredAmount stays the nominal 8, paidAmount is
        // now 8 too), 3 genuinely still owe the full 8 zł.
        List<ContributionRequirement> requirements = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            requirements.add(requirement("covered-" + i, "8", "8", ContributionRequirementStatus.PAID));
        }
        for (int i = 0; i < 3; i++) {
            requirements.add(requirement("pending-" + i, "8", "0", ContributionRequirementStatus.PENDING));
        }
        CollectionDetails details = new CollectionDetails(collection("8"), requirements, List.of());

        CollectionProgressResponse response = CollectionProgressResponse.from(details);

        assertEquals(16, response.studentsCount());
        assertEquals(13, response.studentsPaidCount());
        assertEquals(new BigDecimal("128"), response.totalRequired()); // 16 * 8, the nominal total - not 24
        assertEquals(new BigDecimal("104"), response.totalPaid()); // 13 * 8 already covered, not 0
        assertEquals(81, response.percentComplete()); // 104/128, floored - not 0%
    }

    @Test
    void aStudentWhoPaidTheRemainderAfterAPartialPiggyBankSweepCountsFully() {
        // Base 30 zł, 10 zł already saved -> requiredAmount stays the nominal 30, the 10 zł
        // is swept into a real Contribution at creation (paidAmount=10, still PENDING since
        // 10 < 30), then the parent pays the remaining 20 zł via a second real Contribution.
        ContributionRequirement requirement = requirement("s1", "30", "30", ContributionRequirementStatus.PAID);
        CollectionDetails details = new CollectionDetails(collection("30"), List.of(requirement), List.of());

        CollectionProgressResponse response = CollectionProgressResponse.from(details);

        assertEquals(new BigDecimal("30"), response.totalRequired());
        assertEquals(new BigDecimal("30"), response.totalPaid());
        assertEquals(100, response.percentComplete());
        assertEquals(1, response.studentsPaidCount());
    }

    @Test
    void aStudentWhoHasNotPaidAnythingAndHadNoPriorBalanceShowsZeroProgress() {
        ContributionRequirement requirement = requirement("s1", "8", "0", ContributionRequirementStatus.PENDING);
        CollectionDetails details = new CollectionDetails(collection("8"), List.of(requirement), List.of());

        CollectionProgressResponse response = CollectionProgressResponse.from(details);

        assertEquals(new BigDecimal("8"), response.totalRequired());
        assertEquals(BigDecimal.ZERO, response.totalPaid());
        assertEquals(0, response.percentComplete());
        assertEquals(0, response.studentsPaidCount());
    }
}

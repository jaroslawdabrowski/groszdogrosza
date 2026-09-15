package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementPolicyTest {

    private static Contribution contribution(String parentId, String amount) {
        return new Contribution("c-" + parentId, "collection-1", parentId, new BigDecimal(amount),
                ContributionSource.MANUAL, null, Instant.now());
    }

    @Test
    void evenSurplusSplitEquallyAmongContributingParents() {
        // 4 parents paid 50 zl each = 200 zl, gift cost 160 zl -> 40 zl surplus / 4 = 10 zl each.
        List<Contribution> contributions = List.of(
                contribution("p1", "50.00"),
                contribution("p2", "50.00"),
                contribution("p3", "50.00"),
                contribution("p4", "50.00"));

        SettlementResult result = SettlementPolicy.settle(contributions, new BigDecimal("160.00"));

        assertEquals(new BigDecimal("200.00"), result.totalContributed());
        assertEquals(new BigDecimal("40.00"), result.totalSurplus());
        assertEquals(new BigDecimal("0.00"), result.totalShortfall());
        assertEquals(4, result.parentSettlements().size());
        for (SettlementResult.ParentSettlement settlement : result.parentSettlements()) {
            assertEquals(new BigDecimal("10.00"), settlement.leftoverToCredit());
        }
    }

    @Test
    void unevenSurplusGivesOddGroszToEarliestPayers() {
        // 3 parents paid 50 zl each = 150 zl, cost 100 zl -> 50 zl (5000 gr) surplus / 3
        // = 1666 gr base + 2 gr remainder, handed to the first two payers in payment order.
        List<Contribution> contributions = List.of(
                contribution("p1", "50.00"),
                contribution("p2", "50.00"),
                contribution("p3", "50.00"));

        SettlementResult result = SettlementPolicy.settle(contributions, new BigDecimal("100.00"));

        assertEquals(new BigDecimal("50.00"), result.totalSurplus());
        assertEquals(new BigDecimal("16.67"), result.parentSettlements().get(0).leftoverToCredit());
        assertEquals(new BigDecimal("16.67"), result.parentSettlements().get(1).leftoverToCredit());
        assertEquals(new BigDecimal("16.66"), result.parentSettlements().get(2).leftoverToCredit());

        BigDecimal sumOfLeftovers = result.parentSettlements().stream()
                .map(SettlementResult.ParentSettlement::leftoverToCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(result.totalSurplus(), sumOfLeftovers);
    }

    @Test
    void noSurplusWhenCostExactlyMatchesContributions() {
        List<Contribution> contributions = List.of(
                contribution("p1", "80.00"),
                contribution("p2", "80.00"));

        SettlementResult result = SettlementPolicy.settle(contributions, new BigDecimal("160.00"));

        assertEquals(new BigDecimal("0.00"), result.totalSurplus());
        assertEquals(new BigDecimal("0.00"), result.totalShortfall());
        result.parentSettlements().forEach(s -> assertEquals(new BigDecimal("0.00"), s.leftoverToCredit()));
    }

    @Test
    void overspentCollectionReportsShortfallAndNoLeftover() {
        // Cost exceeded what was actually collected - e.g. settled before everyone paid.
        List<Contribution> contributions = List.of(
                contribution("p1", "50.00"),
                contribution("p2", "50.00"));

        SettlementResult result = SettlementPolicy.settle(contributions, new BigDecimal("160.00"));

        assertEquals(new BigDecimal("100.00"), result.totalContributed());
        assertEquals(new BigDecimal("0.00"), result.totalSurplus());
        assertEquals(new BigDecimal("60.00"), result.totalShortfall());
        result.parentSettlements().forEach(s -> assertEquals(new BigDecimal("0.00"), s.leftoverToCredit()));
    }

    @Test
    void nonContributingParentGetsNoShareOfSurplusEvenIfRequirementWasZero() {
        // p3 never paid anything (e.g. their requirement was fully covered by a prior
        // piggy bank balance) - they must not receive any of the leftover.
        List<Contribution> contributions = List.of(
                contribution("p1", "60.00"),
                contribution("p2", "60.00"));

        SettlementResult result = SettlementPolicy.settle(contributions, new BigDecimal("100.00"));

        assertEquals(2, result.parentSettlements().size());
        assertTrue(result.parentSettlements().stream()
                .noneMatch(s -> s.parentId().equals("p3")));
    }

    @Test
    void multipleContributionsFromSameParentAreSummedAndOrderedByFirstPayment() {
        List<Contribution> contributions = List.of(
                contribution("p1", "30.00"),
                contribution("p2", "50.00"),
                contribution("p1", "20.00")); // p1's second, smaller top-up

        SettlementResult result = SettlementPolicy.settle(contributions, new BigDecimal("0.00"));

        assertEquals("p1", result.parentSettlements().get(0).parentId());
        assertEquals(new BigDecimal("50.00"), result.parentSettlements().get(0).amountPaid());
        assertEquals("p2", result.parentSettlements().get(1).parentId());
    }
}

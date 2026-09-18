package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementPolicyTest {

    private static Contribution contribution(String studentId, String amount) {
        return new Contribution("c-" + studentId, "collection-1", studentId, new BigDecimal(amount),
                ContributionSource.MANUAL, null, Instant.now());
    }

    @Test
    void evenSurplusSplitEquallyAmongContributingStudents() {
        // 4 students were paid for 50 zl each = 200 zl, gift cost 160 zl -> 40 zl surplus / 4 = 10 zl each.
        List<Contribution> contributions = List.of(
                contribution("p1", "50.00"),
                contribution("p2", "50.00"),
                contribution("p3", "50.00"),
                contribution("p4", "50.00"));

        SettlementResult result = SettlementPolicy.settle(contributions, new BigDecimal("160.00"));

        assertEquals(new BigDecimal("200.00"), result.totalContributed());
        assertEquals(new BigDecimal("40.00"), result.totalSurplus());
        assertEquals(new BigDecimal("0.00"), result.totalShortfall());
        assertEquals(4, result.studentSettlements().size());
        for (SettlementResult.StudentSettlement settlement : result.studentSettlements()) {
            assertEquals(new BigDecimal("10.00"), settlement.leftoverToCredit());
        }
    }

    @Test
    void unevenSurplusGivesOddGroszToEarliestPayers() {
        // 3 students were paid for 50 zl each = 150 zl, cost 100 zl -> 50 zl (5000 gr) surplus / 3
        // = 1666 gr base + 2 gr remainder, handed to the first two payers in payment order.
        List<Contribution> contributions = List.of(
                contribution("p1", "50.00"),
                contribution("p2", "50.00"),
                contribution("p3", "50.00"));

        SettlementResult result = SettlementPolicy.settle(contributions, new BigDecimal("100.00"));

        assertEquals(new BigDecimal("50.00"), result.totalSurplus());
        assertEquals(new BigDecimal("16.67"), result.studentSettlements().get(0).leftoverToCredit());
        assertEquals(new BigDecimal("16.67"), result.studentSettlements().get(1).leftoverToCredit());
        assertEquals(new BigDecimal("16.66"), result.studentSettlements().get(2).leftoverToCredit());

        BigDecimal sumOfLeftovers = result.studentSettlements().stream()
                .map(SettlementResult.StudentSettlement::leftoverToCredit)
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
        result.studentSettlements().forEach(s -> assertEquals(new BigDecimal("0.00"), s.leftoverToCredit()));
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
        result.studentSettlements().forEach(s -> assertEquals(new BigDecimal("0.00"), s.leftoverToCredit()));
    }

    @Test
    void nonContributingStudentGetsNoShareOfSurplusEvenIfRequirementWasZero() {
        // p3 never paid anything (e.g. their requirement was fully covered by a prior
        // piggy bank balance) - they must not receive any of the leftover.
        List<Contribution> contributions = List.of(
                contribution("p1", "60.00"),
                contribution("p2", "60.00"));

        SettlementResult result = SettlementPolicy.settle(contributions, new BigDecimal("100.00"));

        assertEquals(2, result.studentSettlements().size());
        assertTrue(result.studentSettlements().stream()
                .noneMatch(s -> s.studentId().equals("p3")));
    }

    @Test
    void multipleContributionsFromSameStudentAreSummedAndOrderedByFirstPayment() {
        List<Contribution> contributions = List.of(
                contribution("p1", "30.00"),
                contribution("p2", "50.00"),
                contribution("p1", "20.00")); // p1's second, smaller top-up

        SettlementResult result = SettlementPolicy.settle(contributions, new BigDecimal("0.00"));

        assertEquals("p1", result.studentSettlements().get(0).studentId());
        assertEquals(new BigDecimal("50.00"), result.studentSettlements().get(0).amountPaid());
        assertEquals("p2", result.studentSettlements().get(1).studentId());
    }
}

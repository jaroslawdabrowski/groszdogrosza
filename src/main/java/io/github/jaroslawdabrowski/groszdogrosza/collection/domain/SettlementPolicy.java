package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure decision logic for settling a collection: given what everyone actually paid in and
 * what the thing being bought actually cost, work out each contributing student's leftover
 * share to credit back to their piggy bank.
 *
 * <p>Deliberately a plain function with no framework dependency, no repository access, no
 * side effects - testable with plain JUnit, same pattern as
 * {@code ChargeDecisionPolicy} in the sibling "pvopt" project. All the actual I/O
 * (persisting contributions, crediting piggy banks, writing ledger entries) happens in
 * {@code application.CollectionService}, which calls this and then acts on the result.
 *
 * <h2>The rule</h2>
 * Surplus = total contributed − actual cost (floored at zero - an underpaid collection
 * produces a {@link SettlementResult#totalShortfall()} instead, never a negative leftover).
 * The surplus is split <b>equally among students whose family actually contributed
 * something</b> to this collection - a student who nobody paid for gets no share of the
 * leftover, even if the requirement said they owed 0 (fully covered by piggy bank) or nobody
 * simply ever paid.
 *
 * <h2>Rounding rule</h2>
 * All money math here is done in integer grosz (1/100 zł) to avoid floating/decimal
 * rounding ambiguity. {@code totalSurplusGrosz / contributingStudents} is integer division
 * (floored); whatever doesn't divide evenly (0 to n-1 grosz) is handed out one grosz at a
 * time to the students who were paid for <b>first</b>, in the order their first contribution
 * to this collection appears in the input list. This is an arbitrary but deterministic and
 * auditable tie-break - "first payer(s) get the odd grosz" - and is exercised explicitly by
 * {@code SettlementPolicyTest#unevenSurplusGivesOddGroszToEarliestPayers}.
 */
public final class SettlementPolicy {

    private SettlementPolicy() {
    }

    public static SettlementResult settle(List<Contribution> contributions, BigDecimal actualCostSpent) {
        if (actualCostSpent.signum() < 0) {
            throw new IllegalArgumentException("actualCostSpent cannot be negative: " + actualCostSpent);
        }

        // Sum per student, preserving the order each student FIRST appears in the list -
        // that order is what "earliest payer" means for the remainder tie-break below.
        Map<String, Long> paidGroszByStudent = new LinkedHashMap<>();
        long totalContributedGrosz = 0L;
        for (Contribution contribution : contributions) {
            long amountGrosz = toGrosz(contribution.amount());
            paidGroszByStudent.merge(contribution.studentId(), amountGrosz, Long::sum);
            totalContributedGrosz += amountGrosz;
        }

        long actualCostGrosz = toGrosz(actualCostSpent);
        long surplusGrosz = Math.max(0L, totalContributedGrosz - actualCostGrosz);
        long shortfallGrosz = Math.max(0L, actualCostGrosz - totalContributedGrosz);

        List<String> contributingStudentIdsInOrder = new ArrayList<>();
        for (Map.Entry<String, Long> entry : paidGroszByStudent.entrySet()) {
            if (entry.getValue() > 0) {
                contributingStudentIdsInOrder.add(entry.getKey());
            }
        }

        int contributorCount = contributingStudentIdsInOrder.size();
        long baseShareGrosz = contributorCount == 0 ? 0L : surplusGrosz / contributorCount;
        long remainderGrosz = contributorCount == 0 ? 0L : surplusGrosz % contributorCount;

        List<SettlementResult.StudentSettlement> studentSettlements = new ArrayList<>();
        for (int i = 0; i < contributorCount; i++) {
            String studentId = contributingStudentIdsInOrder.get(i);
            long leftoverGrosz = baseShareGrosz + (i < remainderGrosz ? 1L : 0L);
            studentSettlements.add(new SettlementResult.StudentSettlement(
                    studentId,
                    fromGrosz(paidGroszByStudent.get(studentId)),
                    fromGrosz(leftoverGrosz)));
        }

        return new SettlementResult(
                fromGrosz(totalContributedGrosz),
                actualCostSpent,
                fromGrosz(surplusGrosz),
                fromGrosz(shortfallGrosz),
                List.copyOf(studentSettlements));
    }

    private static long toGrosz(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).unscaledValue().longValueExact();
    }

    private static BigDecimal fromGrosz(long grosz) {
        return BigDecimal.valueOf(grosz, 2);
    }
}

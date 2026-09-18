package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param totalShortfall positive only when {@code actualCostSpent} exceeded what was
 *                       actually contributed - e.g. the treasurer settled before everyone
 *                       paid. {@code CollectionService} surfaces this back to the caller
 *                       rather than silently ignoring it; nothing here decides how to
 *                       cover a shortfall, that's a manual, human decision.
 * @param studentSettlements one entry per student who contributed something, in the order
 *                           they first paid - see {@link SettlementPolicy} for the
 *                           rounding/ordering rule.
 */
public record SettlementResult(
        BigDecimal totalContributed,
        BigDecimal actualCostSpent,
        BigDecimal totalSurplus,
        BigDecimal totalShortfall,
        List<StudentSettlement> studentSettlements) {

    public record StudentSettlement(String studentId, BigDecimal amountPaid, BigDecimal leftoverToCredit) {
    }
}

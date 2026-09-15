package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.SettlementResult;
import java.math.BigDecimal;
import java.util.List;

public record SettlementResultResponse(
        BigDecimal totalContributed, BigDecimal actualCostSpent, BigDecimal totalSurplus,
        BigDecimal totalShortfall, List<ParentSettlementResponse> parentSettlements) {

    static SettlementResultResponse from(SettlementResult result) {
        return new SettlementResultResponse(
                result.totalContributed(), result.actualCostSpent(), result.totalSurplus(), result.totalShortfall(),
                result.parentSettlements().stream()
                        .map(s -> new ParentSettlementResponse(s.parentId(), s.amountPaid(), s.leftoverToCredit()))
                        .toList());
    }

    public record ParentSettlementResponse(String parentId, BigDecimal amountPaid, BigDecimal leftoverToCredit) {
    }
}

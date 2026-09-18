package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.SettlementResult;
import java.math.BigDecimal;
import java.util.List;

public record SettlementResultResponse(
        BigDecimal totalContributed, BigDecimal actualCostSpent, BigDecimal totalSurplus,
        BigDecimal totalShortfall, List<StudentSettlementResponse> studentSettlements) {

    static SettlementResultResponse from(SettlementResult result) {
        return new SettlementResultResponse(
                result.totalContributed(), result.actualCostSpent(), result.totalSurplus(), result.totalShortfall(),
                result.studentSettlements().stream()
                        .map(s -> new StudentSettlementResponse(s.studentId(), s.amountPaid(), s.leftoverToCredit()))
                        .toList());
    }

    public record StudentSettlementResponse(String studentId, BigDecimal amountPaid, BigDecimal leftoverToCredit) {
    }
}

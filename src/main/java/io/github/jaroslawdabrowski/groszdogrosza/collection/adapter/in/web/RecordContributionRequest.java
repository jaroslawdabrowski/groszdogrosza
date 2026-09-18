package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import java.math.BigDecimal;

public record RecordContributionRequest(String studentId, BigDecimal amount) {
}

package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Contribution;
import java.math.BigDecimal;

/**
 * The treasurer manually records a payment (cash handed over, a transfer that didn't
 * auto-match, etc). Always {@code ContributionSource.MANUAL} - the automatic path from
 * bankstatement goes through {@link ApplyAutomaticContributionUseCase} instead, which
 * carries a bank transaction reference for idempotency.
 */
public interface RecordManualContributionUseCase {

    Contribution recordManualContribution(String collectionId, String studentId, BigDecimal amount);
}

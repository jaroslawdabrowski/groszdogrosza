package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param bankTransactionReference null unless {@code source == BANK_STATEMENT_AUTO}; the
 *                                 bank's own transaction reference (or a hash of the
 *                                 transaction if the bank doesn't expose one), used as the
 *                                 idempotency key by
 *                                 {@code bankstatement.port.out.ProcessedTransactionRepositoryPort}
 *                                 so the same bank transaction is never booked twice.
 */
public record Contribution(
        String id,
        String collectionId,
        String studentId,
        BigDecimal amount,
        ContributionSource source,
        String bankTransactionReference,
        Instant receivedAt) {

    public Contribution {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Contribution amount must be positive: " + amount);
        }
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Contribution;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionSource;
import java.math.BigDecimal;

/**
 * Internal port used by the {@code bankstatement} context to book money against a
 * collection - never called from a web resource directly. Two callers, two
 * {@link ContributionSource} values: a matched bank transaction
 * ({@code BANK_STATEMENT_AUTO}, with a bank reference for idempotency) or piggy bank
 * balance being swept into an active requirement ({@code PIGGY_BANK_APPLIED}, no bank
 * reference). See {@code bankstatement.domain.ContributionAllocationPolicy} for how the
 * amount to apply here is decided.
 */
public interface ApplyAutomaticContributionUseCase {

    Contribution applyContribution(
            String collectionId,
            String studentId,
            BigDecimal amount,
            ContributionSource source,
            String bankTransactionReference);
}

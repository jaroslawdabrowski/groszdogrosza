package io.github.jaroslawdabrowski.groszdogrosza.parent.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import java.math.BigDecimal;

/**
 * Decreases a parent's piggy bank balance, e.g. when it is applied towards a collection's
 * {@code ContributionRequirement}. Never called with an amount larger than the current
 * balance - callers are expected to compute the applied amount via
 * {@code bankstatement.domain.ContributionAllocationPolicy} first.
 */
public interface DebitPiggyBankUseCase {

    Parent debitPiggyBank(String parentId, BigDecimal amount);
}

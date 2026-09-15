package io.github.jaroslawdabrowski.groszdogrosza.parent.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import java.math.BigDecimal;

/**
 * Increases a parent's piggy bank balance. Called internally by other bounded contexts
 * (bankstatement after an auto-matched transaction, collection after a settlement leftover)
 * - deliberately not exposed on any {@code adapter.in.web} resource, since a parent never
 * credits their own piggy bank directly.
 */
public interface CreditPiggyBankUseCase {

    Parent creditPiggyBank(String parentId, BigDecimal amount);
}

package io.github.jaroslawdabrowski.groszdogrosza.parent.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import java.math.BigDecimal;

/**
 * Treasurer-only, web-facing piggy bank top-up - distinct from
 * {@link CreditPiggyBankUseCase} (which is the internal primitive also used by settlement
 * and automatic bank-transaction booking) in that this one also writes a ledger entry,
 * since it's a standalone action a human takes, not a step inside a larger flow that
 * already records its own ledger entries. Typical use: the treasurer pays for something out
 * of their own pocket/account (so it never shows up as an incoming bank transaction - see
 * {@code bankstatement.application.BankStatementProcessingService}, which deliberately
 * ignores transactions matched to the treasurer's own Parent record) and wants that
 * reflected as available balance for the next collection.
 */
public interface CreditPiggyBankManuallyUseCase {

    Parent creditPiggyBankManually(String parentId, BigDecimal amount);
}

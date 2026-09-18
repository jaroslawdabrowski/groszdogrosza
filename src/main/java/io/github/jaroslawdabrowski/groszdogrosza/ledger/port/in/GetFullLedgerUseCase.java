package io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEntry;
import java.util.List;

/**
 * Backs the treasurer-only global activity feed ("log wszystkich transakcji") - every
 * student's ledger entries in one place, newest first. A regular parent only ever sees their
 * own child's entries via {@link GetLedgerForStudentUseCase} - see {@code AuthorizationSupport}.
 */
public interface GetFullLedgerUseCase {

    List<LedgerEntry> getFullLedger();
}

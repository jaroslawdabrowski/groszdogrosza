package io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEntry;
import java.util.List;

public interface GetLedgerForStudentUseCase {

    List<LedgerEntry> getLedgerFor(String studentId);
}

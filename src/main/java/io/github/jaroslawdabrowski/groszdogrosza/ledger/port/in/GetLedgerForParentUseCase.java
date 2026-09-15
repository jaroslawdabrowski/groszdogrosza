package io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEntry;
import java.util.List;

public interface GetLedgerForParentUseCase {

    List<LedgerEntry> getLedgerFor(String parentId);
}

package io.github.jaroslawdabrowski.groszdogrosza.ledger.port.out;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEntry;
import java.util.List;

public interface LedgerRepositoryPort {

    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> findByParentId(String parentId);
}

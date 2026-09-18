package io.github.jaroslawdabrowski.groszdogrosza.ledger.port.out;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEntry;
import java.util.List;

public interface LedgerRepositoryPort {

    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> findByParentId(String parentId);

    /** Every entry across every parent, newest first - backs the treasurer-only global
     * activity feed. See {@code adapter.out.persistence.LedgerDynamoDbAdapter} for why this
     * is a full table scan rather than a query (entries are partitioned per-parent, so
     * there's no single partition key that spans all of them). */
    List<LedgerEntry> findAll();
}

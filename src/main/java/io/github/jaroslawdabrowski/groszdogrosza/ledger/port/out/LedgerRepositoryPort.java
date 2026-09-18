package io.github.jaroslawdabrowski.groszdogrosza.ledger.port.out;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEntry;
import java.util.List;

public interface LedgerRepositoryPort {

    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> findByStudentId(String studentId);

    /** Every entry across every student, newest first - backs the treasurer-only global
     * activity feed. See {@code adapter.out.persistence.LedgerDynamoDbAdapter} for why this
     * is a full table scan rather than a query (entries are partitioned per-student, so
     * there's no single partition key that spans all of them). */
    List<LedgerEntry> findAll();
}

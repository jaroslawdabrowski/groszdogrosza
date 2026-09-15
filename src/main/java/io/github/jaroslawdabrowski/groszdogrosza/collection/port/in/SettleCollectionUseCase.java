package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.SettlementResult;
import java.math.BigDecimal;

/**
 * Settles an ACTIVE collection: runs {@code SettlementPolicy} against its recorded
 * contributions and the given actual cost, credits each contributing parent's piggy bank
 * with their leftover share, writes a {@code COLLECTION_SETTLED} ledger entry per
 * contributing parent, and marks the collection SETTLED.
 */
public interface SettleCollectionUseCase {

    SettlementResult settleCollection(String collectionId, BigDecimal actualCostSpent);
}

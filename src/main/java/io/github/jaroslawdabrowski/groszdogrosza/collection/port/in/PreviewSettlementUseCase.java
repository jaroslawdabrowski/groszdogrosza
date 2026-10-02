package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.SettlementResult;
import java.math.BigDecimal;

/**
 * What settling an ACTIVE collection at the given cost WOULD do - the same
 * {@code SettlementPolicy} run {@code SettleCollectionUseCase} commits, but nothing is
 * credited, logged or saved. Lets the treasurer see who gets how much back (or the
 * shortfall) before the irreversible settle.
 */
public interface PreviewSettlementUseCase {

    SettlementResult previewSettlement(String collectionId, BigDecimal actualCostSpent);
}

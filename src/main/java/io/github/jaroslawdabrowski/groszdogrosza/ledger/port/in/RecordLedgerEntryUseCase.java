package io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEntry;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEventType;
import java.util.Map;

/**
 * The only way any other bounded context writes to the ledger - collection, parent and
 * bankstatement all depend on this port instead of touching {@code LedgerRepositoryPort}
 * directly, so every event type funnels through one place (useful if entry creation ever
 * needs a cross-cutting concern added, e.g. an outbound notification).
 */
public interface RecordLedgerEntryUseCase {

    LedgerEntry record(String parentId, LedgerEventType eventType, Map<String, String> params);
}

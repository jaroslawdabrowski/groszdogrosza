package io.github.jaroslawdabrowski.groszdogrosza.ledger.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEntry;
import java.time.Instant;
import java.util.Map;

/**
 * Same shape as {@link LedgerEntryResponse} plus {@code parentName} - the global feed spans
 * every parent, so (unlike a single parent's own ledger page, which already knows whose
 * entries they're looking at) each entry needs to say whose it is. Rendering still goes
 * through the same {@code ledger.<eventType>} i18n keys, with {@code parentName} as an
 * additional placeholder alongside {@code params}.
 */
public record GlobalLedgerEntryResponse(
        String id, String parentId, String parentName, String eventType, Instant occurredAt, Map<String, String> params) {

    static GlobalLedgerEntryResponse from(LedgerEntry entry, String parentName) {
        return new GlobalLedgerEntryResponse(entry.id(), entry.parentId(), parentName, entry.eventType().name(),
                entry.occurredAt(), entry.params());
    }
}

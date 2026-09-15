package io.github.jaroslawdabrowski.groszdogrosza.ledger.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEntry;
import java.time.Instant;
import java.util.Map;

/**
 * Deliberately ships {@code eventType} + {@code params} as-is, no rendered message - the
 * frontend renders the sentence via i18n key {@code ledger.<eventType>} with these params
 * as placeholders. See {@code LedgerEventType} javadoc.
 */
public record LedgerEntryResponse(String id, String eventType, Instant occurredAt, Map<String, String> params) {

    static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(entry.id(), entry.eventType().name(), entry.occurredAt(), entry.params());
    }
}

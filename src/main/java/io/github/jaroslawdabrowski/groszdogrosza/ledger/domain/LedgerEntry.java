package io.github.jaroslawdabrowski.groszdogrosza.ledger.domain;

import java.time.Instant;
import java.util.Map;

/**
 * One line in a student's auditable event journal. {@code params} deliberately holds
 * String-typed values only (already-formatted amounts, ids, titles) - simple to store as a
 * DynamoDB map attribute and simple for the frontend's i18n interpolation to consume
 * directly, no further type coercion needed.
 */
public record LedgerEntry(
        String id,
        String studentId,
        LedgerEventType eventType,
        Instant occurredAt,
        Map<String, String> params) {

    public LedgerEntry {
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}

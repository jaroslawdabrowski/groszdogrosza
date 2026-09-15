package io.github.jaroslawdabrowski.groszdogrosza.platform.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Tiny AttributeValue conversion helpers shared by the DynamoDB adapters - deliberately
 * not a full object-mapping layer (no annotations, no reflection): each adapter still
 * writes its own explicit item shape, this just removes the AttributeValue.builder()...build()
 * boilerplate for the handful of types actually used (String, BigDecimal, Instant, a flat
 * String-to-String map for LedgerEntry.params, and nullable Strings).
 */
public final class Attr {

    private Attr() {
    }

    public static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    /** Null-safe: DynamoDB has a real NULL type rather than a missing attribute, used for optional fields. */
    public static AttributeValue sOrNull(String value) {
        return value == null ? AttributeValue.builder().nul(true).build() : s(value);
    }

    public static AttributeValue n(BigDecimal value) {
        return AttributeValue.builder().n(value.toPlainString()).build();
    }

    public static AttributeValue instant(Instant value) {
        return s(value.toString());
    }

    public static AttributeValue map(Map<String, String> value) {
        return AttributeValue.builder()
                .m(value.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> s(e.getValue()))))
                .build();
    }

    public static String str(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value == null ? null : value.s();
    }

    public static String strOrNull(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value == null || Boolean.TRUE.equals(value.nul()) ? null : value.s();
    }

    public static BigDecimal decimal(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.n());
    }

    public static Instant instant(Map<String, AttributeValue> item, String key) {
        return Instant.parse(item.get(key).s());
    }

    public static Map<String, String> stringMap(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        if (value == null || value.m() == null) {
            return Map.of();
        }
        return value.m().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().s()));
    }
}

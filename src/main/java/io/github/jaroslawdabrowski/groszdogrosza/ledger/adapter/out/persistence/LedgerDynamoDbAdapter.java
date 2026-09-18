package io.github.jaroslawdabrowski.groszdogrosza.ledger.adapter.out.persistence;

import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.instant;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.map;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.s;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.str;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.stringMap;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEntry;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEventType;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.out.LedgerRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

/**
 * Key layout: {@code pk = "PARENT#<parentId>"}, {@code sk = "LEDGER#<occurredAt ISO-8601>#<id>"}.
 * The ISO-8601 instant sorts lexicographically the same as chronologically, so a Query
 * with {@code ScanIndexForward = false} returns a parent's journal newest-first with no
 * separate index needed. The entry id is appended to the sort key only to guarantee
 * uniqueness if two entries ever land in the same millisecond (e.g. a settlement crediting
 * several parents back-to-back).
 */
@ApplicationScoped
public class LedgerDynamoDbAdapter implements LedgerRepositoryPort {

    private static final String SK_PREFIX = "LEDGER#";

    @Inject
    DynamoDbClient dynamoDbClient;

    @ConfigProperty(name = "groszdogrosza.dynamodb.table-name")
    String tableName;

    @Override
    public LedgerEntry save(LedgerEntry entry) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(toItem(entry))
                .build());
        return entry;
    }

    @Override
    public List<LedgerEntry> findByParentId(String parentId) {
        return dynamoDbClient.query(QueryRequest.builder()
                        .tableName(tableName)
                        .keyConditionExpression("pk = :pk and begins_with(sk, :skPrefix)")
                        .expressionAttributeValues(Map.of(
                                ":pk", s(pk(parentId)),
                                ":skPrefix", s(SK_PREFIX)))
                        .scanIndexForward(false)
                        .build())
                .items().stream()
                .map(LedgerDynamoDbAdapter::fromItem)
                .toList();
    }

    @Override
    public List<LedgerEntry> findAll() {
        // Full table scan filtered by sk prefix - entries are partitioned per-parent
        // (pk = "PARENT#<id>"), so there is no single partition a Query could target for
        // "every entry across every parent". Fine at this app's scale (see
        // ParentDynamoDbAdapter.findAll's javadoc for the same accepted tradeoff); sorted
        // in application code since Scan gives no ordering guarantee.
        return dynamoDbClient.scan(ScanRequest.builder()
                        .tableName(tableName)
                        .filterExpression("begins_with(sk, :skPrefix)")
                        .expressionAttributeValues(Map.of(":skPrefix", s(SK_PREFIX)))
                        .build())
                .items().stream()
                .map(LedgerDynamoDbAdapter::fromItem)
                .sorted(Comparator.comparing(LedgerEntry::occurredAt).reversed())
                .toList();
    }

    private static String pk(String parentId) {
        return "PARENT#" + parentId;
    }

    private static Map<String, AttributeValue> toItem(LedgerEntry entry) {
        return Map.of(
                "pk", s(pk(entry.parentId())),
                "sk", s(SK_PREFIX + entry.occurredAt() + "#" + entry.id()),
                "id", s(entry.id()),
                "parentId", s(entry.parentId()),
                "eventType", s(entry.eventType().name()),
                "occurredAt", instant(entry.occurredAt()),
                "params", map(entry.params()));
    }

    private static LedgerEntry fromItem(Map<String, AttributeValue> item) {
        return new LedgerEntry(
                str(item, "id"),
                str(item, "parentId"),
                LedgerEventType.valueOf(str(item, "eventType")),
                instant(item, "occurredAt"),
                stringMap(item, "params"));
    }
}

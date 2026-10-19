package io.github.jaroslawdabrowski.groszdogrosza.platform.persistence;

import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.s;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.str;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

/**
 * The reads every class-scoped adapter goes through, so that "only this class's data" is
 * enforced in one place rather than remembered in each query (see docs/MULTI-CLASS.md,
 * "Isolation between classes").
 *
 * <p>Every class-owned item (student, parent, collection, ledger entry) carries a
 * {@code classId} attribute. An item belongs to a class only when that attribute matches -
 * an item with no {@code classId} at all (written before the multi-class migration, and not
 * migrated yet) belongs to no class and is invisible, never treated as "everyone's".
 *
 * <p>Scans and queries are paginated: a single DynamoDB Scan/Query call returns at most 1 MB,
 * and the original adapters read only that first page, which would have silently dropped
 * records once the table grew.
 */
public final class ClassScopedReads {

    private ClassScopedReads() {
    }

    /** True only when the item exists and was written for exactly this class. */
    public static boolean belongsTo(Map<String, AttributeValue> item, String classId) {
        return item != null && !item.isEmpty() && classId != null && classId.equals(str(item, "classId"));
    }

    /** A single item by key, but only if it belongs to the class - otherwise empty, exactly
     *  as if it didn't exist (so an id from another class is a plain "not found"). */
    public static Optional<Map<String, AttributeValue>> getInClass(
            DynamoDbClient dynamoDbClient, String tableName, Map<String, AttributeValue> key, String classId) {
        Map<String, AttributeValue> item = dynamoDbClient.getItem(b -> b.tableName(tableName).key(key)).item();
        return belongsTo(item, classId) ? Optional.of(item) : Optional.empty();
    }

    /**
     * Every item matching {@code filterExpression} that belongs to the class, across all scan
     * pages. The class condition is added here, not left to the caller.
     */
    public static List<Map<String, AttributeValue>> scanInClass(
            DynamoDbClient dynamoDbClient, String tableName, String filterExpression,
            Map<String, AttributeValue> values, String classId) {
        Map<String, AttributeValue> allValues = new HashMap<>(values);
        allValues.put(":gg_classId", s(classId));
        ScanRequest request = ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("(" + filterExpression + ") and classId = :gg_classId")
                .expressionAttributeValues(allValues)
                .build();
        return dynamoDbClient.scanPaginator(request).items().stream().toList();
    }

    /** Every item matching the filter regardless of class - only for the few reads that must
     *  cross classes by design (finding which classes a logged-in email belongs to). */
    public static List<Map<String, AttributeValue>> scanAll(
            DynamoDbClient dynamoDbClient, String tableName, String filterExpression, Map<String, AttributeValue> values) {
        ScanRequest request = ScanRequest.builder()
                .tableName(tableName)
                .filterExpression(filterExpression)
                .expressionAttributeValues(values)
                .build();
        return dynamoDbClient.scanPaginator(request).items().stream().toList();
    }

    /** A paginated Query, keeping only the class's items. */
    public static List<Map<String, AttributeValue>> queryInClass(
            DynamoDbClient dynamoDbClient, QueryRequest request, String classId) {
        return dynamoDbClient.queryPaginator(request).items().stream()
                .filter(item -> belongsTo(item, classId))
                .toList();
    }

    /** A paginated Query with no class filter - for item collections already proven to
     *  belong to the class through their parent (a collection's requirements/contributions). */
    public static List<Map<String, AttributeValue>> queryAll(DynamoDbClient dynamoDbClient, QueryRequest request) {
        return dynamoDbClient.queryPaginator(request).items().stream().toList();
    }
}

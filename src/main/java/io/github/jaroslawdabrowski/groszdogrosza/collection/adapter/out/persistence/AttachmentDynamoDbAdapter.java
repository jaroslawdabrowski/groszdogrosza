package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.out.persistence;

import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.instant;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.s;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.str;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.CollectionAttachment;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.out.AttachmentRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

/**
 * Shares the same table/partition as {@code CollectionDynamoDbAdapter} - {@code pk =
 * "COLLECTION#<collectionId>"}, {@code sk = "DOCUMENT#<attachmentId>"} - a new sk prefix
 * alongside the existing REQUIREMENT#/CONTRIBUTION# ones, per CLAUDE.md's key layout
 * convention. Kept as its own adapter/port rather than folded into
 * {@code CollectionRepositoryPort} since it's a genuinely separate concern (file metadata,
 * not money).
 */
@ApplicationScoped
public class AttachmentDynamoDbAdapter implements AttachmentRepositoryPort {

    private static final String DOCUMENT_SK_PREFIX = "DOCUMENT#";

    @Inject
    DynamoDbClient dynamoDbClient;

    @ConfigProperty(name = "groszdogrosza.dynamodb.table-name")
    String tableName;

    private static String collectionPk(String collectionId) {
        return "COLLECTION#" + collectionId;
    }

    @Override
    public CollectionAttachment saveAttachment(CollectionAttachment attachment) {
        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(Map.of(
                "pk", s(collectionPk(attachment.collectionId())),
                "sk", s(DOCUMENT_SK_PREFIX + attachment.id()),
                "id", s(attachment.id()),
                "collectionId", s(attachment.collectionId()),
                "fileName", s(attachment.fileName()),
                "contentType", s(attachment.contentType()),
                "sizeBytes", AttributeValue.builder().n(Long.toString(attachment.sizeBytes())).build(),
                "s3Key", s(attachment.s3Key()),
                "uploadedAt", instant(attachment.uploadedAt()))).build());
        return attachment;
    }

    @Override
    public List<CollectionAttachment> findAttachmentsByCollectionId(String collectionId) {
        return dynamoDbClient.query(QueryRequest.builder()
                        .tableName(tableName)
                        .keyConditionExpression("pk = :pk and begins_with(sk, :skPrefix)")
                        .expressionAttributeValues(
                                Map.of(":pk", s(collectionPk(collectionId)), ":skPrefix", s(DOCUMENT_SK_PREFIX)))
                        .build())
                .items().stream()
                .map(AttachmentDynamoDbAdapter::attachmentFromItem)
                .toList();
    }

    @Override
    public Optional<CollectionAttachment> findAttachment(String collectionId, String attachmentId) {
        Map<String, AttributeValue> item = dynamoDbClient.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of("pk", s(collectionPk(collectionId)), "sk", s(DOCUMENT_SK_PREFIX + attachmentId)))
                        .build())
                .item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(attachmentFromItem(item));
    }

    @Override
    public void deleteAttachment(String collectionId, String attachmentId) {
        dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("pk", s(collectionPk(collectionId)), "sk", s(DOCUMENT_SK_PREFIX + attachmentId)))
                .build());
    }

    private static CollectionAttachment attachmentFromItem(Map<String, AttributeValue> item) {
        return new CollectionAttachment(
                str(item, "id"), str(item, "collectionId"), str(item, "fileName"), str(item, "contentType"),
                Long.parseLong(item.get("sizeBytes").n()), str(item, "s3Key"), instant(item, "uploadedAt"));
    }
}

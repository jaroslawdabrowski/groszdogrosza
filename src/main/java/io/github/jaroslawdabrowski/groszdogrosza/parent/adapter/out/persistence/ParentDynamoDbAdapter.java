package io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.out.persistence;

import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.s;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.sOrNull;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.str;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.strOrNull;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.ParentRole;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.PaymentInfo;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.out.ParentRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.ClassScopedReads;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

/**
 * Key layout: {@code pk = "PARENT#<id>"}, {@code sk = "PARENT"} - one item per parent, no
 * related sub-items, so a sort key isn't strictly needed here, but keeping the same pk/sk
 * shape as every other item in the table simplifies {@code DynamoDbTableInitializer} and any
 * future admin tooling that just wants to scan "everything".
 */
@ApplicationScoped
public class ParentDynamoDbAdapter implements ParentRepositoryPort {

    private static final String SK = "PARENT";

    @Inject
    DynamoDbClient dynamoDbClient;

    @ConfigProperty(name = "groszdogrosza.dynamodb.table-name")
    String tableName;

    @Override
    public Parent save(Parent parent) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(toItem(parent))
                .build());
        return parent;
    }

    @Override
    public Optional<Parent> findById(String classId, String parentId) {
        return ClassScopedReads.getInClass(dynamoDbClient, tableName, key(parentId), classId)
                .map(ParentDynamoDbAdapter::fromItem);
    }

    @Override
    public List<Parent> findByEmail(String email) {
        // Across every class on purpose: this is how a login finds the classes it belongs to.
        // Email comparison is case-insensitive in application code (DynamoDB filter
        // expressions have no case-insensitive equality). A record with no classId (not
        // migrated yet) belongs to no class, so it grants nothing.
        return ClassScopedReads.scanAll(dynamoDbClient, tableName, "sk = :sk", Map.of(":sk", s(SK))).stream()
                .map(ParentDynamoDbAdapter::fromItem)
                .filter(parent -> parent.classId() != null)
                .filter(parent -> parent.email() != null && parent.email().equalsIgnoreCase(email))
                .toList();
    }

    @Override
    public List<Parent> findAllInClass(String classId) {
        return ClassScopedReads.scanInClass(dynamoDbClient, tableName, "sk = :sk", Map.of(":sk", s(SK)), classId)
                .stream()
                .map(ParentDynamoDbAdapter::fromItem)
                .toList();
    }

    @Override
    public List<Parent> findByStudentId(String classId, String studentId) {
        return findAllInClass(classId).stream()
                .filter(parent -> studentId.equals(parent.studentId()))
                .toList();
    }

    @Override
    public void deleteById(String classId, String parentId) {
        // Conditional: deletes only the class's own parent record.
        try {
            dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(key(parentId))
                    .conditionExpression("classId = :classId")
                    .expressionAttributeValues(Map.of(":classId", s(classId)))
                    .build());
        } catch (ConditionalCheckFailedException e) {
            // Not this class's parent (or already gone) - nothing to delete.
        }
    }

    private static Map<String, AttributeValue> key(String parentId) {
        return Map.of("pk", s(pk(parentId)), "sk", s(SK));
    }

    private static String pk(String parentId) {
        return "PARENT#" + parentId;
    }

    private static Map<String, AttributeValue> toItem(Parent parent) {
        return Map.ofEntries(
                Map.entry("pk", s(pk(parent.id()))),
                Map.entry("sk", s(SK)),
                Map.entry("id", s(parent.id())),
                Map.entry("classId", s(parent.classId())),
                Map.entry("studentId", sOrNull(parent.studentId())),
                Map.entry("firstName", s(parent.firstName())),
                Map.entry("lastName", s(parent.lastName())),
                Map.entry("email", s(parent.email())),
                Map.entry("expectedSenderName", s(parent.expectedSenderName())),
                Map.entry("cognitoSubjectId", sOrNull(parent.cognitoSubjectId())),
                Map.entry("role", s(parent.role().name())),
                Map.entry("bankAccountNumber", sOrNull(parent.paymentInfo() == null ? null : parent.paymentInfo().bankAccountNumber())),
                Map.entry("blikPhoneNumber", sOrNull(parent.paymentInfo() == null ? null : parent.paymentInfo().blikPhoneNumber())));
    }

    private static Parent fromItem(Map<String, AttributeValue> item) {
        String bankAccountNumber = strOrNull(item, "bankAccountNumber");
        String blikPhoneNumber = strOrNull(item, "blikPhoneNumber");
        PaymentInfo paymentInfo = bankAccountNumber == null && blikPhoneNumber == null
                ? null
                : new PaymentInfo(bankAccountNumber, blikPhoneNumber);
        return new Parent(
                str(item, "id"),
                str(item, "classId"),
                strOrNull(item, "studentId"),
                str(item, "firstName"),
                str(item, "lastName"),
                str(item, "email"),
                str(item, "expectedSenderName"),
                strOrNull(item, "cognitoSubjectId"),
                ParentRole.valueOf(str(item, "role")),
                paymentInfo);
    }
}

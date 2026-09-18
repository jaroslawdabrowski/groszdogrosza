package io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.out.persistence;

import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.decimal;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.n;
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
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

/**
 * Key layout: {@code pk = "PARENT#<id>"}, {@code sk = "PARENT"} - one item per parent, no
 * related sub-items (unlike collection/ledger), so a sort key isn't strictly needed here,
 * but keeping the same pk/sk shape as every other item in the table simplifies
 * {@link DynamoDbTableInitializer} and any future admin tooling that just wants to scan
 * "everything".
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
    public Optional<Parent> findById(String parentId) {
        Map<String, AttributeValue> item = dynamoDbClient.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of("pk", s(pk(parentId)), "sk", s(SK)))
                        .build())
                .item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(fromItem(item));
    }

    @Override
    public Optional<Parent> findByEmail(String email) {
        // Full scan is fine at this scale - same tradeoff as findAll() below. Email
        // comparison is done case-insensitively in application code (DynamoDB filter
        // expressions have no case-insensitive equality), which is fine given the low
        // parent count this app is scoped for.
        return dynamoDbClient.scan(ScanRequest.builder()
                        .tableName(tableName)
                        .filterExpression("sk = :sk")
                        .expressionAttributeValues(Map.of(":sk", s(SK)))
                        .build())
                .items().stream()
                .map(ParentDynamoDbAdapter::fromItem)
                .filter(parent -> parent.email().equalsIgnoreCase(email))
                .findFirst();
    }

    @Override
    public List<Parent> findAll() {
        // A full scan is fine at this scale (a single class's worth of parents, a handful
        // of reads a day) - see DynamoDbTableInitializer javadoc on the single-table layout.
        return dynamoDbClient.scan(ScanRequest.builder()
                        .tableName(tableName)
                        .filterExpression("sk = :sk")
                        .expressionAttributeValues(Map.of(":sk", s(SK)))
                        .build())
                .items().stream()
                .map(ParentDynamoDbAdapter::fromItem)
                .toList();
    }

    private static String pk(String parentId) {
        return "PARENT#" + parentId;
    }

    private static Map<String, AttributeValue> toItem(Parent parent) {
        return Map.ofEntries(
                Map.entry("pk", s(pk(parent.id()))),
                Map.entry("sk", s(SK)),
                Map.entry("id", s(parent.id())),
                Map.entry("firstName", s(parent.firstName())),
                Map.entry("lastName", s(parent.lastName())),
                Map.entry("email", s(parent.email())),
                Map.entry("expectedSenderName", s(parent.expectedSenderName())),
                Map.entry("cognitoSubjectId", sOrNull(parent.cognitoSubjectId())),
                Map.entry("role", s(parent.role().name())),
                Map.entry("piggyBankBalance", n(parent.piggyBankBalance())),
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
                str(item, "firstName"),
                str(item, "lastName"),
                str(item, "email"),
                str(item, "expectedSenderName"),
                strOrNull(item, "cognitoSubjectId"),
                ParentRole.valueOf(str(item, "role")),
                decimal(item, "piggyBankBalance"),
                paymentInfo);
    }
}

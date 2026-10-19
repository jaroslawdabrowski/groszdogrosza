package io.github.jaroslawdabrowski.groszdogrosza.schoolclass.adapter.out.persistence;

import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.instant;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.s;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.sOrNull;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.str;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.strOrNull;

import io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.ClassScopedReads;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.PaymentInfo;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.SchoolClass;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.out.SchoolClassRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Key layout:
 * <pre>
 * CLASS#&lt;id&gt; / CLASS                 - the class itself (name, public link token, payment info)
 * CLASS#&lt;id&gt; / TREASURER#&lt;email&gt;   - one per treasurer of the class (email lowercased)
 * </pre>
 * Parent membership isn't stored here: a user is a parent in a class when a Parent record
 * with their email is attached to one of the class's students.
 */
@ApplicationScoped
public class SchoolClassDynamoDbAdapter implements SchoolClassRepositoryPort {

    private static final String CLASS_SK = "CLASS";
    private static final String TREASURER_SK_PREFIX = "TREASURER#";

    @Inject
    DynamoDbClient dynamoDbClient;

    @ConfigProperty(name = "groszdogrosza.dynamodb.table-name")
    String tableName;

    @Override
    public Optional<SchoolClass> findById(String classId) {
        Map<String, AttributeValue> item = dynamoDbClient.getItem(b -> b.tableName(tableName)
                .key(Map.of("pk", s(pk(classId)), "sk", s(CLASS_SK)))).item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(fromItem(item));
    }

    @Override
    public Optional<SchoolClass> findByPublicToken(String publicToken) {
        return ClassScopedReads.scanAll(dynamoDbClient, tableName, "sk = :sk and publicToken = :token",
                        Map.of(":sk", s(CLASS_SK), ":token", s(publicToken)))
                .stream()
                .findFirst()
                .map(SchoolClassDynamoDbAdapter::fromItem);
    }

    @Override
    public SchoolClass save(SchoolClass schoolClass) {
        PaymentInfo paymentInfo = schoolClass.paymentInfo();
        dynamoDbClient.putItem(b -> b.tableName(tableName).item(Map.of(
                "pk", s(pk(schoolClass.id())),
                "sk", s(CLASS_SK),
                "id", s(schoolClass.id()),
                "classId", s(schoolClass.id()),
                "name", s(schoolClass.name()),
                "publicToken", s(schoolClass.publicToken()),
                "bankAccountNumber", sOrNull(paymentInfo == null ? null : paymentInfo.bankAccountNumber()),
                "blikPhoneNumber", sOrNull(paymentInfo == null ? null : paymentInfo.blikPhoneNumber()),
                "createdAt", instant(schoolClass.createdAt()))));
        return schoolClass;
    }

    @Override
    public boolean isTreasurer(String classId, String email) {
        Map<String, AttributeValue> item = dynamoDbClient.getItem(b -> b.tableName(tableName)
                .key(Map.of("pk", s(pk(classId)), "sk", s(TREASURER_SK_PREFIX + normalize(email))))).item();
        return item != null && !item.isEmpty();
    }

    @Override
    public List<String> findClassIdsWhereTreasurer(String email) {
        return ClassScopedReads.scanAll(dynamoDbClient, tableName, "sk = :sk",
                        Map.of(":sk", s(TREASURER_SK_PREFIX + normalize(email))))
                .stream()
                .map(item -> str(item, "classId"))
                .toList();
    }

    private static String pk(String classId) {
        return "CLASS#" + classId;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static SchoolClass fromItem(Map<String, AttributeValue> item) {
        String bankAccountNumber = strOrNull(item, "bankAccountNumber");
        String blikPhoneNumber = strOrNull(item, "blikPhoneNumber");
        PaymentInfo paymentInfo = bankAccountNumber == null && blikPhoneNumber == null
                ? null
                : new PaymentInfo(bankAccountNumber, blikPhoneNumber);
        return new SchoolClass(str(item, "id"), str(item, "name"), str(item, "publicToken"), paymentInfo,
                io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.instant(item, "createdAt"));
    }
}

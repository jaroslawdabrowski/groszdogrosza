package io.github.jaroslawdabrowski.groszdogrosza.student.adapter.out.persistence;

import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.decimal;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.n;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.s;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.str;

import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.out.StudentRepositoryPort;
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
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

/** Key layout: {@code pk = "STUDENT#<id>"}, {@code sk = "STUDENT"} - same one-item-per-entity
 *  shape as {@code ParentDynamoDbAdapter}. */
@ApplicationScoped
public class StudentDynamoDbAdapter implements StudentRepositoryPort {

    private static final String SK = "STUDENT";

    @Inject
    DynamoDbClient dynamoDbClient;

    @ConfigProperty(name = "groszdogrosza.dynamodb.table-name")
    String tableName;

    @Override
    public Student save(Student student) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(toItem(student))
                .build());
        return student;
    }

    @Override
    public Optional<Student> findById(String studentId) {
        Map<String, AttributeValue> item = dynamoDbClient.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of("pk", s(pk(studentId)), "sk", s(SK)))
                        .build())
                .item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(fromItem(item));
    }

    @Override
    public List<Student> findAll() {
        return dynamoDbClient.scan(ScanRequest.builder()
                        .tableName(tableName)
                        .filterExpression("sk = :sk")
                        .expressionAttributeValues(Map.of(":sk", s(SK)))
                        .build())
                .items().stream()
                .map(StudentDynamoDbAdapter::fromItem)
                .toList();
    }

    @Override
    public void deleteById(String studentId) {
        dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("pk", s(pk(studentId)), "sk", s(SK)))
                .build());
    }

    private static String pk(String studentId) {
        return "STUDENT#" + studentId;
    }

    private static Map<String, AttributeValue> toItem(Student student) {
        return Map.of(
                "pk", s(pk(student.id())),
                "sk", s(SK),
                "id", s(student.id()),
                "firstName", s(student.firstName()),
                "lastName", s(student.lastName()),
                "piggyBankBalance", n(student.piggyBankBalance()));
    }

    private static Student fromItem(Map<String, AttributeValue> item) {
        return new Student(
                str(item, "id"),
                str(item, "firstName"),
                str(item, "lastName"),
                decimal(item, "piggyBankBalance"));
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.platform.persistence;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Creates the single application table on startup if it doesn't exist yet - this is what
 * makes `quarkus:dev` (and its Localstack-backed Dev Services DynamoDB) work with zero
 * manual setup. In real AWS the table is created once by Terraform
 * (`aws_dynamodb_table.app` in infra/main/main.tf) instead - the class-level
 * {@code @IfBuildProfile("dev")} means this bean doesn't exist at all in the packaged
 * Lambda (confirmed the hard way: a method-level {@code @IfBuildProfile} on just
 * {@code onStart} was NOT enough - the bean itself still got registered and its
 * {@code @Observes StartupEvent} still fired outside dev mode. Class-level is what actually
 * works, matching {@code BankStatementDevPoller}'s already-correct pattern.)
 *
 * <p><b>This used to be unconditional and it took down production</b>: the original
 * assumption was that `CreateTable` against an already-existing table would just throw a
 * harmless {@code ResourceInUseException} (caught below) even outside dev mode - but the
 * Lambda's execution role deliberately does NOT grant `dynamodb:CreateTable` at all (least
 * privilege - Terraform is the only thing that should ever create/delete the table), so the
 * real failure on every cold start was an uncaught `DynamoDbException` (AccessDenied) from
 * this `@Observes StartupEvent` method, which crashed Quarkus's boot entirely (confirmed:
 * every request 502'd, CloudWatch showed "Failed to start quarkus" caused by "is not
 * authorized to perform: dynamodb:CreateTable"). Do not remove the `@IfBuildProfile("dev")`
 * without also granting the Lambda role `dynamodb:CreateTable` (which would defeat the
 * point of Terraform owning the table's lifecycle).
 *
 * <p>Single-table design: partition key {@code pk}, sort key {@code sk}. See each
 * adapter's class javadoc for its own key layout (e.g. {@code STUDENT#<id>} /
 * {@code STUDENT}, {@code PARENT#<id>} / {@code PARENT}, {@code COLLECTION#<id>} /
 * {@code REQUIREMENT#<studentId>}, ...). This diverges from the sibling "turboorders"
 * project's single-hash-key table - groszdogrosza has several related entity types per
 * aggregate (a collection's requirements and contributions), which a sort key models
 * naturally; turboorders' one entity type didn't need one.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DynamoDbTableInitializer {

    private static final Logger LOG = Logger.getLogger(DynamoDbTableInitializer.class);

    @Inject
    DynamoDbClient dynamoDbClient;

    @ConfigProperty(name = "groszdogrosza.dynamodb.table-name")
    String tableName;

    void onStart(@Observes StartupEvent event) {
        try {
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(tableName)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                    .build());
            LOG.infof("Created DynamoDB table '%s'", tableName);
        } catch (ResourceInUseException e) {
            LOG.debugf("DynamoDB table '%s' already exists", tableName);
        }
    }
}

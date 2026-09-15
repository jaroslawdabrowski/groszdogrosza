package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.adapter.out.persistence;

import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.s;

import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.out.ProcessedTransactionRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * Key layout: {@code pk = "PROCESSEDTX#<bankReference>"}, {@code sk = "PROCESSEDTX"}.
 * {@link #claimProcessing} is a single conditional PutItem
 * ({@code attribute_not_exists(pk)}) - a genuine compare-and-swap, not a separate
 * GetItem-then-PutItem, so two overlapping polls (or a retried EventBridge invocation)
 * can never both believe they own the same bank reference.
 *
 * <p>This does NOT make the whole booking operation atomic with the claim - the claim and
 * the actual piggy-bank credit in {@code BankStatementProcessingService} are still two
 * separate writes. A crash between them leaves the reference claimed but the money never
 * credited (a silent miss, logged, needing manual reconciliation via the ledger) rather
 * than the credit happening twice (the original bug this replaces). True cross-aggregate
 * atomicity would need a DynamoDB {@code TransactWriteItems} spanning the parent and
 * processed-tx items, deferred until this is exercised against a real mailbox.
 */
@ApplicationScoped
public class ProcessedTransactionDynamoDbAdapter implements ProcessedTransactionRepositoryPort {

    private static final String SK = "PROCESSEDTX";

    @Inject
    DynamoDbClient dynamoDbClient;

    @ConfigProperty(name = "groszdogrosza.dynamodb.table-name")
    String tableName;

    @Override
    public boolean claimProcessing(String bankReference) {
        try {
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(Map.of(
                            "pk", s(pk(bankReference)),
                            "sk", s(SK),
                            "bankReference", s(bankReference),
                            "processedAt", s(Instant.now().toString())))
                    .conditionExpression("attribute_not_exists(pk)")
                    .build());
            return true;
        } catch (ConditionalCheckFailedException alreadyClaimed) {
            return false;
        }
    }

    private static String pk(String bankReference) {
        return "PROCESSEDTX#" + bankReference;
    }
}

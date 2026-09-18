package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.out.persistence;

import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.decimal;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.instant;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.n;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.s;
import static io.github.jaroslawdabrowski.groszdogrosza.platform.persistence.Attr.str;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.CollectionStatus;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Contribution;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirementStatus;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionSource;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.out.CollectionRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

/**
 * Key layout - three item shapes share one table (see {@code DynamoDbTableInitializer}):
 * <ul>
 *   <li>Collection: {@code pk = "COLLECTION#<id>"}, {@code sk = "COLLECTION"}</li>
 *   <li>ContributionRequirement: {@code pk = "COLLECTION#<collectionId>"},
 *       {@code sk = "REQUIREMENT#<studentId>"} - one per (collection, student) pair, a
 *       Query on the collection's pk with a "REQUIREMENT#" sk prefix lists them all.</li>
 *   <li>Contribution: {@code pk = "COLLECTION#<collectionId>"},
 *       {@code sk = "CONTRIBUTION#<id>"}</li>
 * </ul>
 *
 * <p>{@link #findActivePendingRequirementsForStudent(String)} is the one query that cuts
 * across collections instead of staying within one partition - there's no GSI for it yet
 * (would need one keyed by studentId), so it scans the whole table filtering by sk prefix
 * and re-checks each matching requirement's parent collection status individually. Fine at
 * this app's scale (one class, a handful of collections a year); revisit with a GSI if
 * that ever stops being true.
 */
@ApplicationScoped
public class CollectionDynamoDbAdapter implements CollectionRepositoryPort {

    private static final String COLLECTION_SK = "COLLECTION";
    private static final String REQUIREMENT_SK_PREFIX = "REQUIREMENT#";
    private static final String CONTRIBUTION_SK_PREFIX = "CONTRIBUTION#";

    @Inject
    DynamoDbClient dynamoDbClient;

    @ConfigProperty(name = "groszdogrosza.dynamodb.table-name")
    String tableName;

    private static String collectionPk(String collectionId) {
        return "COLLECTION#" + collectionId;
    }

    // --- Collection ---

    @Override
    public Collection saveCollection(Collection collection) {
        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(Map.of(
                "pk", s(collectionPk(collection.id())),
                "sk", s(COLLECTION_SK),
                "id", s(collection.id()),
                "title", s(collection.title()),
                "description", s(collection.description() == null ? "" : collection.description()),
                "status", s(collection.status().name()),
                "baseAmountPerStudent", n(collection.baseAmountPerStudent()),
                "createdAt", instant(collection.createdAt()))).build());
        return collection;
    }

    @Override
    public Optional<Collection> findCollectionById(String collectionId) {
        Map<String, AttributeValue> item = dynamoDbClient.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of("pk", s(collectionPk(collectionId)), "sk", s(COLLECTION_SK)))
                        .build())
                .item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(collectionFromItem(item));
    }

    @Override
    public List<Collection> findAllCollections() {
        return dynamoDbClient.scan(ScanRequest.builder()
                        .tableName(tableName)
                        .filterExpression("sk = :sk")
                        .expressionAttributeValues(Map.of(":sk", s(COLLECTION_SK)))
                        .build())
                .items().stream()
                .map(CollectionDynamoDbAdapter::collectionFromItem)
                .toList();
    }

    private static Collection collectionFromItem(Map<String, AttributeValue> item) {
        return new Collection(
                str(item, "id"), str(item, "title"), str(item, "description"),
                CollectionStatus.valueOf(str(item, "status")),
                decimal(item, "baseAmountPerStudent"),
                instant(item, "createdAt"));
    }

    // --- ContributionRequirement ---

    @Override
    public ContributionRequirement saveRequirement(ContributionRequirement requirement) {
        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(Map.of(
                "pk", s(collectionPk(requirement.collectionId())),
                "sk", s(REQUIREMENT_SK_PREFIX + requirement.studentId()),
                "id", s(requirement.id()),
                "collectionId", s(requirement.collectionId()),
                "studentId", s(requirement.studentId()),
                "requiredAmount", n(requirement.requiredAmount()),
                "paidAmount", n(requirement.paidAmount()),
                "status", s(requirement.status().name()))).build());
        return requirement;
    }

    @Override
    public List<ContributionRequirement> findRequirementsByCollectionId(String collectionId) {
        return queryByPkAndSkPrefix(collectionPk(collectionId), REQUIREMENT_SK_PREFIX).stream()
                .map(CollectionDynamoDbAdapter::requirementFromItem)
                .toList();
    }

    @Override
    public Optional<ContributionRequirement> findRequirement(String collectionId, String studentId) {
        Map<String, AttributeValue> item = dynamoDbClient.getItem(GetItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of("pk", s(collectionPk(collectionId)), "sk", s(REQUIREMENT_SK_PREFIX + studentId)))
                        .build())
                .item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(requirementFromItem(item));
    }

    @Override
    public List<ContributionRequirement> findActivePendingRequirementsForStudent(String studentId) {
        // See class javadoc: a table scan, filtered by sk suffix and status, then
        // re-checked per-collection for ACTIVE status and sorted oldest-collection-first
        // (what ContributionAllocationPolicy expects).
        List<ContributionRequirement> candidates = dynamoDbClient.scan(ScanRequest.builder()
                        .tableName(tableName)
                        .filterExpression("sk = :sk and #status = :pending")
                        .expressionAttributeNames(Map.of("#status", "status"))
                        .expressionAttributeValues(Map.of(
                                ":sk", s(REQUIREMENT_SK_PREFIX + studentId),
                                ":pending", s(ContributionRequirementStatus.PENDING.name())))
                        .build())
                .items().stream()
                .map(CollectionDynamoDbAdapter::requirementFromItem)
                .toList();

        record CandidateWithCollection(ContributionRequirement requirement, Collection collection) {
        }

        return candidates.stream()
                .map(r -> new CandidateWithCollection(r, findCollectionById(r.collectionId()).orElse(null)))
                .filter(c -> c.collection() != null && c.collection().status() == CollectionStatus.ACTIVE)
                .sorted(Comparator.comparing(c -> c.collection().createdAt()))
                .map(CandidateWithCollection::requirement)
                .toList();
    }

    private static ContributionRequirement requirementFromItem(Map<String, AttributeValue> item) {
        return new ContributionRequirement(
                str(item, "id"), str(item, "collectionId"), str(item, "studentId"),
                decimal(item, "requiredAmount"), decimal(item, "paidAmount"),
                ContributionRequirementStatus.valueOf(str(item, "status")));
    }

    // --- Contribution ---

    @Override
    public Contribution saveContribution(Contribution contribution) {
        dynamoDbClient.putItem(PutItemRequest.builder().tableName(tableName).item(Map.of(
                "pk", s(collectionPk(contribution.collectionId())),
                "sk", s(CONTRIBUTION_SK_PREFIX + contribution.id()),
                "id", s(contribution.id()),
                "collectionId", s(contribution.collectionId()),
                "studentId", s(contribution.studentId()),
                "amount", n(contribution.amount()),
                "source", s(contribution.source().name()),
                "bankTransactionReference", s(contribution.bankTransactionReference() == null ? "" : contribution.bankTransactionReference()),
                "receivedAt", instant(contribution.receivedAt()))).build());
        return contribution;
    }

    @Override
    public List<Contribution> findContributionsByCollectionId(String collectionId) {
        return queryByPkAndSkPrefix(collectionPk(collectionId), CONTRIBUTION_SK_PREFIX).stream()
                .map(CollectionDynamoDbAdapter::contributionFromItem)
                .toList();
    }

    private static Contribution contributionFromItem(Map<String, AttributeValue> item) {
        String bankReference = str(item, "bankTransactionReference");
        return new Contribution(
                str(item, "id"), str(item, "collectionId"), str(item, "studentId"),
                decimal(item, "amount"), ContributionSource.valueOf(str(item, "source")),
                bankReference == null || bankReference.isEmpty() ? null : bankReference,
                instant(item, "receivedAt"));
    }

    // --- shared query helper ---

    private List<Map<String, AttributeValue>> queryByPkAndSkPrefix(String pk, String skPrefix) {
        return dynamoDbClient.query(QueryRequest.builder()
                        .tableName(tableName)
                        .keyConditionExpression("pk = :pk and begins_with(sk, :skPrefix)")
                        .expressionAttributeValues(Map.of(":pk", s(pk), ":skPrefix", s(skPrefix)))
                        .build())
                .items();
    }
}

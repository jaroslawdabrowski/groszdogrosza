package io.github.jaroslawdabrowski.groszdogrosza.collection.application;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.CollectionNotActiveException;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.CollectionStatus;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Contribution;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirementStatus;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionSource;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.SettlementPolicy;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.SettlementResult;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.ApplyAutomaticContributionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.CreateCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetActiveRequirementsForParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.ListCollectionsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.RecordManualContributionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.SettleCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.out.CollectionRepositoryPort;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEventType;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.RecordLedgerEntryUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreditPiggyBankUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CollectionService implements CreateCollectionUseCase, GetCollectionUseCase, ListCollectionsUseCase,
        RecordManualContributionUseCase, ApplyAutomaticContributionUseCase, GetActiveRequirementsForParentUseCase,
        SettleCollectionUseCase {

    @Inject
    CollectionRepositoryPort collectionRepository;

    @Inject
    ListParentsUseCase listParentsUseCase;

    @Inject
    CreditPiggyBankUseCase creditPiggyBankUseCase;

    @Inject
    RecordLedgerEntryUseCase recordLedgerEntryUseCase;

    @Override
    public Collection createCollection(String title, String description, BigDecimal baseAmountPerParent) {
        Collection collection = new Collection(UUID.randomUUID().toString(), title, description,
                CollectionStatus.ACTIVE, baseAmountPerParent, Instant.now());
        collection = collectionRepository.saveCollection(collection);

        // Snapshot each parent's piggy bank balance NOW and fix the requirement - see the
        // ContributionRequirement javadoc for why this is deliberately not recomputed later.
        for (Parent parent : listParentsUseCase.listParents()) {
            BigDecimal required = baseAmountPerParent.subtract(parent.piggyBankBalance());
            if (required.signum() < 0) {
                required = BigDecimal.ZERO;
            }
            ContributionRequirement requirement = new ContributionRequirement(
                    UUID.randomUUID().toString(), collection.id(), parent.id(), required, BigDecimal.ZERO,
                    ContributionRequirementStatus.PENDING);
            collectionRepository.saveRequirement(requirement);
        }

        return collection;
    }

    @Override
    public Optional<CollectionDetails> getCollection(String collectionId) {
        return collectionRepository.findCollectionById(collectionId).map(collection -> new CollectionDetails(
                collection,
                collectionRepository.findRequirementsByCollectionId(collectionId),
                collectionRepository.findContributionsByCollectionId(collectionId)));
    }

    @Override
    public List<Collection> listCollections() {
        return collectionRepository.findAllCollections();
    }

    @Override
    public Contribution recordManualContribution(String collectionId, String parentId, BigDecimal amount) {
        return bookContribution(collectionId, parentId, amount, ContributionSource.MANUAL, null);
    }

    @Override
    public Contribution applyContribution(
            String collectionId, String parentId, BigDecimal amount, ContributionSource source,
            String bankTransactionReference) {
        return bookContribution(collectionId, parentId, amount, source, bankTransactionReference);
    }

    private Contribution bookContribution(
            String collectionId, String parentId, BigDecimal amount, ContributionSource source,
            String bankTransactionReference) {
        Collection collection = collectionRepository.findCollectionById(collectionId)
                .orElseThrow(() -> new NoSuchElementException("No such collection: " + collectionId));

        Contribution contribution = new Contribution(UUID.randomUUID().toString(), collectionId, parentId, amount,
                source, bankTransactionReference, Instant.now());
        contribution = collectionRepository.saveContribution(contribution);

        collectionRepository.findRequirement(collectionId, parentId).ifPresent(requirement ->
                collectionRepository.saveRequirement(requirement.withAdditionalPayment(amount)));

        recordLedgerEntryUseCase.record(parentId, LedgerEventType.CONTRIBUTION_RECEIVED, java.util.Map.of(
                "collectionId", collectionId,
                "collectionTitle", collection.title(),
                "amount", amount.toPlainString()));

        return contribution;
    }

    @Override
    public List<ContributionRequirement> getActivePendingRequirements(String parentId) {
        return collectionRepository.findActivePendingRequirementsForParent(parentId);
    }

    @Override
    public SettlementResult settleCollection(String collectionId, BigDecimal actualCostSpent) {
        Collection collection = collectionRepository.findCollectionById(collectionId)
                .orElseThrow(() -> new NoSuchElementException("No such collection: " + collectionId));
        if (collection.status() != CollectionStatus.ACTIVE) {
            // Not just a defensive check: without it, a double-clicked "Settle" button or a
            // client retry after a timeout re-runs the block below and credits every
            // contributing parent's leftover a second time for money they were never owed.
            throw new CollectionNotActiveException(collectionId, collection.status());
        }

        List<Contribution> contributions = collectionRepository.findContributionsByCollectionId(collectionId);
        SettlementResult result = SettlementPolicy.settle(contributions, actualCostSpent);

        for (SettlementResult.ParentSettlement settlement : result.parentSettlements()) {
            if (settlement.leftoverToCredit().signum() > 0) {
                creditPiggyBankUseCase.creditPiggyBank(settlement.parentId(), settlement.leftoverToCredit());
                recordLedgerEntryUseCase.record(settlement.parentId(), LedgerEventType.PIGGY_BANK_CREDITED,
                        java.util.Map.of("amount", settlement.leftoverToCredit().toPlainString()));
            }
            recordLedgerEntryUseCase.record(settlement.parentId(), LedgerEventType.COLLECTION_SETTLED,
                    java.util.Map.of(
                            "collectionId", collectionId,
                            "collectionTitle", collection.title(),
                            "leftoverAmount", settlement.leftoverToCredit().toPlainString()));
        }

        collectionRepository.saveCollection(collection.withStatus(CollectionStatus.SETTLED));
        return result;
    }
}

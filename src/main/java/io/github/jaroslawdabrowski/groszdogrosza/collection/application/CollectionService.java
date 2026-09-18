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
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetActiveRequirementsForStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.ListCollectionsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.RecordManualContributionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.SettleCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.out.CollectionRepositoryPort;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEventType;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.RecordLedgerEntryUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.CreditStudentPiggyBankUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.ListStudentsUseCase;
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
        RecordManualContributionUseCase, ApplyAutomaticContributionUseCase, GetActiveRequirementsForStudentUseCase,
        SettleCollectionUseCase {

    @Inject
    CollectionRepositoryPort collectionRepository;

    @Inject
    ListStudentsUseCase listStudentsUseCase;

    @Inject
    CreditStudentPiggyBankUseCase creditStudentPiggyBankUseCase;

    @Inject
    RecordLedgerEntryUseCase recordLedgerEntryUseCase;

    @Override
    public Collection createCollection(String title, String description, BigDecimal baseAmountPerStudent) {
        Collection collection = new Collection(UUID.randomUUID().toString(), title, description,
                CollectionStatus.ACTIVE, baseAmountPerStudent, Instant.now());
        collection = collectionRepository.saveCollection(collection);

        // Snapshot each student's piggy bank balance NOW and fix the requirement - see the
        // ContributionRequirement javadoc for why this is deliberately not recomputed later.
        for (Student student : listStudentsUseCase.listStudents()) {
            BigDecimal required = baseAmountPerStudent.subtract(student.piggyBankBalance());
            if (required.signum() < 0) {
                required = BigDecimal.ZERO;
            }
            ContributionRequirement requirement = new ContributionRequirement(
                    UUID.randomUUID().toString(), collection.id(), student.id(), required, BigDecimal.ZERO,
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
    public Contribution recordManualContribution(String collectionId, String studentId, BigDecimal amount) {
        return bookContribution(collectionId, studentId, amount, ContributionSource.MANUAL, null);
    }

    @Override
    public Contribution applyContribution(
            String collectionId, String studentId, BigDecimal amount, ContributionSource source,
            String bankTransactionReference) {
        return bookContribution(collectionId, studentId, amount, source, bankTransactionReference);
    }

    private Contribution bookContribution(
            String collectionId, String studentId, BigDecimal amount, ContributionSource source,
            String bankTransactionReference) {
        Collection collection = collectionRepository.findCollectionById(collectionId)
                .orElseThrow(() -> new NoSuchElementException("No such collection: " + collectionId));

        Contribution contribution = new Contribution(UUID.randomUUID().toString(), collectionId, studentId, amount,
                source, bankTransactionReference, Instant.now());
        contribution = collectionRepository.saveContribution(contribution);

        collectionRepository.findRequirement(collectionId, studentId).ifPresent(requirement ->
                collectionRepository.saveRequirement(requirement.withAdditionalPayment(amount)));

        recordLedgerEntryUseCase.record(studentId, LedgerEventType.CONTRIBUTION_RECEIVED, java.util.Map.of(
                "collectionId", collectionId,
                "collectionTitle", collection.title(),
                "amount", amount.toPlainString()));

        return contribution;
    }

    @Override
    public List<ContributionRequirement> getActivePendingRequirements(String studentId) {
        return collectionRepository.findActivePendingRequirementsForStudent(studentId);
    }

    @Override
    public SettlementResult settleCollection(String collectionId, BigDecimal actualCostSpent) {
        Collection collection = collectionRepository.findCollectionById(collectionId)
                .orElseThrow(() -> new NoSuchElementException("No such collection: " + collectionId));
        if (collection.status() != CollectionStatus.ACTIVE) {
            // Not just a defensive check: without it, a double-clicked "Settle" button or a
            // client retry after a timeout re-runs the block below and credits every
            // contributing student's leftover a second time for money they were never owed.
            throw new CollectionNotActiveException(collectionId, collection.status());
        }

        List<Contribution> contributions = collectionRepository.findContributionsByCollectionId(collectionId);
        SettlementResult result = SettlementPolicy.settle(contributions, actualCostSpent);

        for (SettlementResult.StudentSettlement settlement : result.studentSettlements()) {
            if (settlement.leftoverToCredit().signum() > 0) {
                creditStudentPiggyBankUseCase.creditPiggyBank(settlement.studentId(), settlement.leftoverToCredit());
                recordLedgerEntryUseCase.record(settlement.studentId(), LedgerEventType.PIGGY_BANK_CREDITED,
                        java.util.Map.of("amount", settlement.leftoverToCredit().toPlainString()));
            }
            recordLedgerEntryUseCase.record(settlement.studentId(), LedgerEventType.COLLECTION_SETTLED,
                    java.util.Map.of(
                            "collectionId", collectionId,
                            "collectionTitle", collection.title(),
                            "leftoverAmount", settlement.leftoverToCredit().toPlainString()));
        }

        collectionRepository.saveCollection(collection.withStatus(CollectionStatus.SETTLED));
        return result;
    }
}

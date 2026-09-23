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
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.RemoveStudentFromCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.SettleCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.out.CollectionRepositoryPort;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerAmounts;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEventType;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.RecordLedgerEntryUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.CreditStudentPiggyBankUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.DebitStudentPiggyBankUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.ListStudentsUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class CollectionService implements CreateCollectionUseCase, GetCollectionUseCase, ListCollectionsUseCase,
        RecordManualContributionUseCase, ApplyAutomaticContributionUseCase, GetActiveRequirementsForStudentUseCase,
        SettleCollectionUseCase, RemoveStudentFromCollectionUseCase {

    @Inject
    CollectionRepositoryPort collectionRepository;

    @Inject
    ListStudentsUseCase listStudentsUseCase;

    @Inject
    CreditStudentPiggyBankUseCase creditStudentPiggyBankUseCase;

    @Inject
    DebitStudentPiggyBankUseCase debitStudentPiggyBankUseCase;

    @Inject
    RecordLedgerEntryUseCase recordLedgerEntryUseCase;

    @Override
    public Collection createCollection(
            String title, String description, BigDecimal baseAmountPerStudent, List<String> includedStudentIds) {
        Collection collection = new Collection(UUID.randomUUID().toString(), title, description,
                CollectionStatus.ACTIVE, baseAmountPerStudent, Instant.now());
        collection = collectionRepository.saveCollection(collection);

        Set<String> included = Set.copyOf(includedStudentIds);

        // Snapshot each INCLUDED student's piggy bank balance NOW and fix the requirement at
        // the collection's nominal per-student amount - see ContributionRequirement's javadoc
        // for why requiredAmount is the nominal amount (not discounted) and why that's
        // deliberately not recomputed later. A student left unchecked on the "who's in this
        // collection" checklist gets no requirement at all, not a zero one - see
        // CreateCollectionUseCase's javadoc for why (an "everyone owes money" collection and
        // a "whoever's coming on the trip owes money" collection are both real cases here) -
        // and, critically, no piggy bank debit either: nothing below ever runs for them.
        for (Student student : listStudentsUseCase.listStudents()) {
            if (!included.contains(student.id())) {
                continue;
            }
            ContributionRequirement requirement = new ContributionRequirement(
                    UUID.randomUUID().toString(), collection.id(), student.id(), baseAmountPerStudent,
                    BigDecimal.ZERO, ContributionRequirementStatus.PENDING);
            collectionRepository.saveRequirement(requirement);

            // A student whose existing piggy bank balance can cover some or all of this
            // requirement immediately gets it swept in right now, exactly like a real
            // incoming payment would (see ContributionAllocationPolicy's javadoc, which
            // documents this as the same "landing spot" idea, and
            // BankStatementProcessingService.bookMatchedTransaction for the pattern this
            // mirrors) - a real piggy-bank debit, a PIGGY_BANK_APPLIED_TO_COLLECTION ledger
            // entry, and a real Contribution (so this student is correctly included if the
            // collection is later settled with a surplus, and correctly refunded if they're
            // removed from the collection before settlement). Found and fixed after a real
            // collection silently marked 13 of 16 students PAID with zero money movement and
            // zero audit trail - a treasurer has no way to trust "already covered" numbers
            // that don't actually move or log anything.
            BigDecimal covered = student.piggyBankBalance().min(baseAmountPerStudent);
            if (covered.signum() > 0) {
                debitStudentPiggyBankUseCase.debitPiggyBank(student.id(), covered);
                // Same params shape as BankStatementProcessingService.bookMatchedTransaction's
                // identical call for the real-payment case - collectionId, amount, no title
                // (the i18n string for this event type doesn't use one).
                recordLedgerEntryUseCase.record(student.id(), LedgerEventType.PIGGY_BANK_APPLIED_TO_COLLECTION,
                        java.util.Map.of(
                                "collectionId", collection.id(),
                                "amount", LedgerAmounts.format(covered)));
                applyContribution(collection.id(), student.id(), covered, ContributionSource.PIGGY_BANK_APPLIED, null);
            }
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
                "amount", LedgerAmounts.format(amount)));

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
                        java.util.Map.of("amount", LedgerAmounts.format(settlement.leftoverToCredit())));
            }
            recordLedgerEntryUseCase.record(settlement.studentId(), LedgerEventType.COLLECTION_SETTLED,
                    java.util.Map.of(
                            "collectionId", collectionId,
                            "collectionTitle", collection.title(),
                            "leftoverAmount", LedgerAmounts.format(settlement.leftoverToCredit())));
        }

        collectionRepository.saveCollection(collection.withStatus(CollectionStatus.SETTLED));
        return result;
    }

    @Override
    public void removeStudentFromCollection(String collectionId, String studentId) {
        Collection collection = collectionRepository.findCollectionById(collectionId)
                .orElseThrow(() -> new NoSuchElementException("No such collection: " + collectionId));
        if (collection.status() != CollectionStatus.ACTIVE) {
            // Same reasoning as settleCollection's guard: a settled collection's numbers are
            // already final and baked into everyone's piggy bank leftover - pulling a
            // student out of it after the fact would need to unwind that settlement, not
            // just delete a requirement.
            throw new CollectionNotActiveException(collectionId, collection.status());
        }
        collectionRepository.findRequirement(collectionId, studentId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Student " + studentId + " is not part of collection " + collectionId));

        List<Contribution> contributions = collectionRepository.findContributionsByCollectionId(collectionId).stream()
                .filter(contribution -> contribution.studentId().equals(studentId))
                .toList();
        BigDecimal refunded = contributions.stream().map(Contribution::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Delete, not zero out - a removed student must vanish from this collection's
        // breakdown entirely, and their contributions must not still be counted the next
        // time this collection is settled (see SettlementPolicy, which sums every
        // Contribution it's handed).
        for (Contribution contribution : contributions) {
            collectionRepository.deleteContribution(collectionId, contribution.id());
        }
        collectionRepository.deleteRequirement(collectionId, studentId);

        if (refunded.signum() > 0) {
            creditStudentPiggyBankUseCase.creditPiggyBank(studentId, refunded);
        }
        recordLedgerEntryUseCase.record(studentId, LedgerEventType.REMOVED_FROM_COLLECTION, java.util.Map.of(
                "collectionId", collectionId,
                "collectionTitle", collection.title(),
                "refundedAmount", LedgerAmounts.format(refunded)));
    }
}

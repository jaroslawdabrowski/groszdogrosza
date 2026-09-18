package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.application;

import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain.BankTransaction;
import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain.ContributionAllocationPolicy;
import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain.ContributionAllocationPolicy.AllocationResult;
import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain.MatchResult;
import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain.PaymentMatchingPolicy;
import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.in.PollBankStatementsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.out.BankStatementFetchPort;
import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.out.BankStatementFetchPort.RawStatementAttachment;
import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.out.ProcessedTransactionRepositoryPort;
import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.out.StatementParserPort;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionSource;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.ApplyAutomaticContributionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetActiveRequirementsForStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEventType;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.RecordLedgerEntryUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.CreditStudentPiggyBankUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.DebitStudentPiggyBankUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.ListStudentsUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Orchestrates one full poll-and-book cycle. This is the ONLY fully-automatic money-moving
 * path in the app - see CLAUDE.md ("Automatic matching without confirmation") for why that
 * was accepted, and why the two safeguards below (ledger entries for every step,
 * idempotency via {@code ProcessedTransactionRepositoryPort}) are non-negotiable.
 *
 * <p>TODO: the "since" lookback is currently a fixed window
 * ({@code groszdogrosza.bankstatement.poll-lookback}), not a persisted cursor of the last
 * successfully processed email - fine for a single daily statement mail (today's email is
 * always well within a multi-day lookback), but a proper cursor (e.g. last processed
 * message's receivedAt, stored via a small DynamoDB item) would be more precise and is
 * worth adding once the IMAP adapter is verified against real mail.
 */
@ApplicationScoped
public class BankStatementProcessingService implements PollBankStatementsUseCase {

    private static final Logger LOG = Logger.getLogger(BankStatementProcessingService.class);

    @Inject
    BankStatementFetchPort fetchPort;

    @Inject
    StatementParserPort parserPort;

    @Inject
    ProcessedTransactionRepositoryPort processedTransactionRepository;

    @Inject
    ListStudentsUseCase listStudentsUseCase;

    @Inject
    ListParentsUseCase listParentsUseCase;

    @Inject
    GetActiveRequirementsForStudentUseCase getActiveRequirementsForStudentUseCase;

    @Inject
    ApplyAutomaticContributionUseCase applyAutomaticContributionUseCase;

    @Inject
    CreditStudentPiggyBankUseCase creditStudentPiggyBankUseCase;

    @Inject
    DebitStudentPiggyBankUseCase debitStudentPiggyBankUseCase;

    @Inject
    RecordLedgerEntryUseCase recordLedgerEntryUseCase;

    @ConfigProperty(name = "groszdogrosza.bankstatement.matching.min-confidence")
    double minConfidence;

    @ConfigProperty(name = "groszdogrosza.bankstatement.poll-lookback-days", defaultValue = "3")
    int lookbackDays;

    @Override
    public PollResult pollAndProcess() {
        Instant since = Instant.now().minus(lookbackDays, ChronoUnit.DAYS);
        List<RawStatementAttachment> attachments = fetchPort.fetchNewStatementsSince(since);
        List<Student> students = listStudentsUseCase.listStudents();
        List<Parent> parents = listParentsUseCase.listParents();

        int seen = 0;
        int matched = 0;
        int unmatched = 0;
        int alreadyProcessed = 0;
        int failed = 0;
        int ignoredTreasurerOwnAccount = 0;

        for (RawStatementAttachment attachment : attachments) {
            List<BankTransaction> transactions;
            try {
                transactions = parserPort.parse(attachment.htmlContent());
            } catch (RuntimeException e) {
                // A mail from the expected sender that isn't shaped like a real "Powiadomienie
                // e-mail" (e.g. IMAP's attachment-disposition fallback picked up something
                // unexpected, or mBank changes the format) must be skipped, not crash the
                // whole poll - every other attachment in this cycle still deserves a chance.
                LOG.errorf(e, "Failed to parse a fetched mail (messageId=%s) as an mBank statement - skipping it",
                        attachment.messageId());
                continue;
            }
            for (BankTransaction transaction : transactions) {
                seen++;

                // Step 0 - must run before any matching below, not after: the treasurer's
                // own child is also a Student in this model, and their surname is very
                // likely the same as the treasurer's own, so a student-surname match could
                // otherwise wrongly catch the treasurer's own outgoing transfer - see
                // PaymentMatchingPolicy's class javadoc.
                if (PaymentMatchingPolicy.matchesTreasurerOwnAccount(transaction.senderName(), parents, minConfidence)) {
                    // The mailbox being polled belongs to the treasurer, so the statement
                    // naturally contains the treasurer's own account activity too - not just
                    // other families paying in. Crediting this would let the treasurer
                    // accidentally double count their own money. The treasurer's own child's
                    // piggy bank is topped up manually instead (see
                    // CreditStudentPiggyBankManuallyUseCase) and swept into collections
                    // exactly like every other student's, via the same
                    // createCollection/settle logic.
                    ignoredTreasurerOwnAccount++;
                    LOG.debugf("Ignoring bank transaction matched to the treasurer's own account, ref %s",
                            transaction.bankReference());
                    continue;
                }

                Optional<MatchResult> match = PaymentMatchingPolicy.match(transaction, students, parents, minConfidence);
                if (match.isEmpty()) {
                    unmatched++;
                    LOG.warnf("Unmatched bank transaction from '%s', amount %s, ref %s - needs manual booking",
                            transaction.senderName(), transaction.amount(), transaction.bankReference());
                    // Deliberately NOT claimed - an unmatched transaction should be retried
                    // on the next poll in case a student/parent is added/corrected before
                    // then, and it must remain visible for the treasurer to book manually.
                    continue;
                }

                // Claim the reference right before booking, not before matching, and as a
                // single conditional write (see ProcessedTransactionRepositoryPort) so two
                // overlapping polls can never both book the same transaction.
                if (!processedTransactionRepository.claimProcessing(transaction.bankReference())) {
                    alreadyProcessed++;
                    continue;
                }

                try {
                    bookMatchedTransaction(transaction, match.get(), students);
                    matched++;
                } catch (RuntimeException e) {
                    // The reference is already claimed at this point, so this transaction
                    // will NOT be retried automatically - it needs manual reconciliation
                    // (check the student's ledger for a PIGGY_BANK_CREDITED entry with this
                    // bankReference to see how far booking got before it failed). Trading a
                    // silent miss for the double-credit this replaces is the deliberate
                    // choice - see ProcessedTransactionDynamoDbAdapter's javadoc. One failed
                    // transaction must not abort the rest of the poll.
                    failed++;
                    LOG.errorf(e, "Failed to book matched bank transaction ref=%s, studentId=%s, amount=%s - "
                                    + "already claimed, will NOT retry automatically, needs manual reconciliation",
                            transaction.bankReference(), match.get().studentId(), transaction.amount());
                }
            }
        }

        return new PollResult(seen, matched, unmatched, alreadyProcessed, failed, ignoredTreasurerOwnAccount);
    }

    private void bookMatchedTransaction(BankTransaction transaction, MatchResult match, List<Student> students) {
        String studentId = match.studentId();
        BigDecimal balanceBeforeThisTransaction = students.stream()
                .filter(s -> s.id().equals(studentId))
                .findFirst()
                .map(Student::piggyBankBalance)
                .orElse(BigDecimal.ZERO);

        // Step 1: land the money in the piggy bank and leave an audit trail - this happens
        // unconditionally, even if it's immediately swept back out in step 2.
        creditStudentPiggyBankUseCase.creditPiggyBank(studentId, transaction.amount());
        recordLedgerEntryUseCase.record(studentId, LedgerEventType.PIGGY_BANK_CREDITED, Map.of(
                "amount", transaction.amount().toPlainString(),
                "bankReference", transaction.bankReference()));

        // Step 2: sweep as much of the (pre-existing balance + this payment) as needed
        // towards whatever this student currently owes on active collections, oldest
        // collection first - not just this one transaction's amount, since an earlier
        // overpayment sitting in the piggy bank is just as spendable here.
        List<ContributionRequirement> activeRequirements = getActiveRequirementsForStudentUseCase
                .getActivePendingRequirements(studentId);
        AllocationResult allocation = ContributionAllocationPolicy.allocate(
                transaction.amount(), balanceBeforeThisTransaction, activeRequirements);

        for (ContributionAllocationPolicy.RequirementAllocation requirementAllocation : allocation.allocations()) {
            debitStudentPiggyBankUseCase.debitPiggyBank(studentId, requirementAllocation.amountApplied());
            recordLedgerEntryUseCase.record(studentId, LedgerEventType.PIGGY_BANK_APPLIED_TO_COLLECTION, Map.of(
                    "collectionId", requirementAllocation.collectionId(),
                    "amount", requirementAllocation.amountApplied().toPlainString()));
            applyAutomaticContributionUseCase.applyContribution(
                    requirementAllocation.collectionId(), studentId, requirementAllocation.amountApplied(),
                    ContributionSource.PIGGY_BANK_APPLIED, transaction.bankReference());
        }
    }
}

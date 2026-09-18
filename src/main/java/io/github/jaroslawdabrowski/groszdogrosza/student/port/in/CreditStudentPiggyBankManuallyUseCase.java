package io.github.jaroslawdabrowski.groszdogrosza.student.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import java.math.BigDecimal;

/**
 * Treasurer-only, web-facing piggy bank top-up - distinct from
 * {@link CreditStudentPiggyBankUseCase} (the internal primitive also used by settlement and
 * automatic bank-transaction booking) in that this one also writes a ledger entry, since
 * it's a standalone action a human takes rather than a step inside a larger flow that
 * already records its own entries. Typical use: a cash payment, or the treasurer's own child
 * being topped up manually (their account is excluded from automatic bank matching - see
 * {@code bankstatement.domain.PaymentMatchingPolicy#matchesTreasurerOwnAccount}).
 */
public interface CreditStudentPiggyBankManuallyUseCase {

    Student creditPiggyBankManually(String studentId, BigDecimal amount);
}

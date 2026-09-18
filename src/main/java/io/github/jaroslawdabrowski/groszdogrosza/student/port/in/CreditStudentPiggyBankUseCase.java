package io.github.jaroslawdabrowski.groszdogrosza.student.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import java.math.BigDecimal;

/**
 * Increases a student's piggy bank balance. Called internally by other bounded contexts
 * (bankstatement after an auto-matched transaction, collection after a settlement leftover)
 * - deliberately not exposed directly on any {@code adapter.in.web} resource; the treasurer's
 * own manual top-up path is {@link CreditStudentPiggyBankManuallyUseCase}, which also writes
 * a ledger entry.
 */
public interface CreditStudentPiggyBankUseCase {

    Student creditPiggyBank(String studentId, BigDecimal amount);
}

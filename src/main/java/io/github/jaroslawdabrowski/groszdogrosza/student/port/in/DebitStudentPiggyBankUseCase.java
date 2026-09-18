package io.github.jaroslawdabrowski.groszdogrosza.student.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import java.math.BigDecimal;

/** Decreases a student's piggy bank balance, e.g. when it's applied towards a
 *  {@code ContributionRequirement}. Never called with an amount larger than the current
 *  balance - callers compute the applied amount via
 *  {@code bankstatement.domain.ContributionAllocationPolicy} first. */
public interface DebitStudentPiggyBankUseCase {

    Student debitPiggyBank(String studentId, BigDecimal amount);
}

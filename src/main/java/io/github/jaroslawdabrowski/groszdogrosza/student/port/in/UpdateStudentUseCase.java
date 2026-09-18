package io.github.jaroslawdabrowski.groszdogrosza.student.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;

/** Treasurer-only. Deliberately does not touch {@code piggyBankBalance} - that has its own
 *  narrower credit/debit use cases. */
public interface UpdateStudentUseCase {

    Student updateStudent(String studentId, String firstName, String lastName);
}

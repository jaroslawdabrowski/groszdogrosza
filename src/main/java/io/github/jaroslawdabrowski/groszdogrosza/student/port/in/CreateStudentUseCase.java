package io.github.jaroslawdabrowski.groszdogrosza.student.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;

/** Treasurer-only. Parents are added separately afterwards, see
 *  {@code parent.port.in.CreateParentUseCase}. */
public interface CreateStudentUseCase {

    Student createStudent(String firstName, String lastName);
}

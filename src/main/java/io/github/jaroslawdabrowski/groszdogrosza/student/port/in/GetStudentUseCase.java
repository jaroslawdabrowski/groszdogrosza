package io.github.jaroslawdabrowski.groszdogrosza.student.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import java.util.Optional;

public interface GetStudentUseCase {

    Optional<Student> getStudent(String studentId);
}

package io.github.jaroslawdabrowski.groszdogrosza.student.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import java.util.List;

public interface ListStudentsUseCase {

    List<Student> listStudents();
}

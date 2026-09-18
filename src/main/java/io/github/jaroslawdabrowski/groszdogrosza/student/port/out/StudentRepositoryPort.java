package io.github.jaroslawdabrowski.groszdogrosza.student.port.out;

import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import java.util.List;
import java.util.Optional;

public interface StudentRepositoryPort {

    Student save(Student student);

    Optional<Student> findById(String studentId);

    List<Student> findAll();

    /** No-op if the id doesn't exist. */
    void deleteById(String studentId);
}

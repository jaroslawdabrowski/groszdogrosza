package io.github.jaroslawdabrowski.groszdogrosza.student.port.out;

import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import java.util.List;
import java.util.Optional;

public interface StudentRepositoryPort {

    Student save(Student student);

    /** Empty when the student doesn't exist OR belongs to another class. */
    Optional<Student> findById(String classId, String studentId);

    List<Student> findAllInClass(String classId);

    /** No-op if the id doesn't exist in this class. */
    void deleteById(String classId, String studentId);
}

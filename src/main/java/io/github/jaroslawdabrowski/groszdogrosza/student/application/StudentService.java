package io.github.jaroslawdabrowski.groszdogrosza.student.application;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEventType;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.RecordLedgerEntryUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.DeleteParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsForStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.CreateStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.CreditStudentPiggyBankManuallyUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.CreditStudentPiggyBankUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.DebitStudentPiggyBankUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.DeleteStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.GetStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.ListStudentsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.UpdateStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.out.StudentRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class StudentService implements CreateStudentUseCase, GetStudentUseCase, ListStudentsUseCase,
        UpdateStudentUseCase, DeleteStudentUseCase, CreditStudentPiggyBankUseCase, DebitStudentPiggyBankUseCase,
        CreditStudentPiggyBankManuallyUseCase {

    @Inject
    StudentRepositoryPort studentRepository;

    @Inject
    ListParentsForStudentUseCase listParentsForStudentUseCase;

    @Inject
    DeleteParentUseCase deleteParentUseCase;

    @Inject
    RecordLedgerEntryUseCase recordLedgerEntryUseCase;

    @Override
    public Student createStudent(String firstName, String lastName) {
        Student student = new Student(UUID.randomUUID().toString(), firstName, lastName, BigDecimal.ZERO);
        return studentRepository.save(student);
    }

    @Override
    public Optional<Student> getStudent(String studentId) {
        return studentRepository.findById(studentId);
    }

    @Override
    public List<Student> listStudents() {
        return studentRepository.findAll();
    }

    @Override
    public Student updateStudent(String studentId, String firstName, String lastName) {
        Student student = requireStudent(studentId);
        Student updated = new Student(student.id(), firstName, lastName, student.piggyBankBalance());
        return studentRepository.save(updated);
    }

    @Override
    public void deleteStudent(String studentId) {
        requireStudent(studentId);
        for (Parent parent : listParentsForStudentUseCase.listParentsForStudent(studentId)) {
            deleteParentUseCase.deleteParent(parent.id());
        }
        studentRepository.deleteById(studentId);
    }

    @Override
    public Student creditPiggyBank(String studentId, BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Cannot credit a negative amount: " + amount);
        }
        Student student = requireStudent(studentId);
        Student updated = withPiggyBankBalance(student, student.piggyBankBalance().add(amount));
        return studentRepository.save(updated);
    }

    @Override
    public Student debitPiggyBank(String studentId, BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Cannot debit a negative amount: " + amount);
        }
        Student student = requireStudent(studentId);
        BigDecimal newBalance = student.piggyBankBalance().subtract(amount);
        if (newBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Cannot debit " + amount + " from piggy bank balance " + student.piggyBankBalance()
                            + " for student " + studentId);
        }
        Student updated = withPiggyBankBalance(student, newBalance);
        return studentRepository.save(updated);
    }

    @Override
    public Student creditPiggyBankManually(String studentId, BigDecimal amount) {
        Student updated = creditPiggyBank(studentId, amount);
        recordLedgerEntryUseCase.record(studentId, LedgerEventType.PIGGY_BANK_CREDITED,
                Map.of("amount", amount.toPlainString()));
        return updated;
    }

    private Student requireStudent(String studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new NoSuchElementException("No such student: " + studentId));
    }

    private static Student withPiggyBankBalance(Student student, BigDecimal newBalance) {
        return new Student(student.id(), student.firstName(), student.lastName(), newBalance);
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.student.application;

import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.SweepPiggyBankIntoActiveCollectionsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerAmounts;
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
import java.util.ArrayList;
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

    @Inject
    SweepPiggyBankIntoActiveCollectionsUseCase sweepPiggyBankIntoActiveCollectionsUseCase;

    @Override
    public Student createStudent(String classId, String firstName, String lastName) {
        Student student = new Student(UUID.randomUUID().toString(), classId, firstName, lastName, BigDecimal.ZERO);
        return studentRepository.save(student);
    }

    @Override
    public Optional<Student> getStudent(String classId, String studentId) {
        return studentRepository.findById(classId, studentId);
    }

    @Override
    public List<Student> listStudents(String classId) {
        // The repository (a DynamoDB Scan) has no ordering guarantee at all - sort here,
        // once, so every caller (the treasurer's student list, the "who's in this
        // collection" checklist, a collection's requirement breakdown, ...) sees the same
        // stable lastName-then-firstName order without having to sort it again itself. See
        // Student.byLastNameThenFirstName's javadoc for why a class roster, specifically,
        // needs Polish collation rather than raw String ordering.
        List<Student> students = new ArrayList<>(studentRepository.findAllInClass(classId));
        students.sort(Student.byLastNameThenFirstName());
        return students;
    }

    @Override
    public Student updateStudent(String classId, String studentId, String firstName, String lastName) {
        Student student = requireStudent(classId, studentId);
        Student updated = new Student(student.id(), student.classId(), firstName, lastName, student.piggyBankBalance());
        return studentRepository.save(updated);
    }

    @Override
    public void deleteStudent(String classId, String studentId) {
        requireStudent(classId, studentId);
        for (Parent parent : listParentsForStudentUseCase.listParentsForStudent(classId, studentId)) {
            deleteParentUseCase.deleteParent(classId, parent.id());
        }
        studentRepository.deleteById(classId, studentId);
    }

    @Override
    public Student creditPiggyBank(String classId, String studentId, BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Cannot credit a negative amount: " + amount);
        }
        Student student = requireStudent(classId, studentId);
        Student updated = student.withPiggyBankBalance(student.piggyBankBalance().add(amount));
        return studentRepository.save(updated);
    }

    @Override
    public Student debitPiggyBank(String classId, String studentId, BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Cannot debit a negative amount: " + amount);
        }
        Student student = requireStudent(classId, studentId);
        BigDecimal newBalance = student.piggyBankBalance().subtract(amount);
        if (newBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Cannot debit " + amount + " from piggy bank balance " + student.piggyBankBalance()
                            + " for student " + studentId);
        }
        Student updated = student.withPiggyBankBalance(newBalance);
        return studentRepository.save(updated);
    }

    @Override
    public Student creditPiggyBankManually(String classId, String studentId, BigDecimal amount) {
        Student updated = creditPiggyBank(classId, studentId, amount);
        recordLedgerEntryUseCase.record(classId, studentId, LedgerEventType.PIGGY_BANK_CREDITED,
                Map.of("amount", LedgerAmounts.format(amount)));
        // A manual credit (typically cash handed to the treasurer) gets exactly the same
        // "sweep into whatever's currently owed on an ACTIVE collection" treatment an
        // automatic bank-transfer match already gets - see SweepPiggyBankIntoActiveCollectionsUseCase's
        // javadoc for the production gap this closes. Re-fetch afterwards: the sweep debits
        // the balance further, so `updated` above is stale the moment it returns.
        sweepPiggyBankIntoActiveCollectionsUseCase.sweep(classId, studentId);
        return requireStudent(classId, studentId);
    }

    private Student requireStudent(String classId, String studentId) {
        return studentRepository.findById(classId, studentId)
                .orElseThrow(() -> new NoSuchElementException("No such student: " + studentId));
    }
}

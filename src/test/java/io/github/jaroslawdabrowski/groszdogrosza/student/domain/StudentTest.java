package io.github.jaroslawdabrowski.groszdogrosza.student.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every student list in this app (the class roster, a collection's requirement breakdown,
 *  the "who's in this collection" checklist, ...) goes through
 *  {@link Student#byLastNameThenFirstName()} - see {@code StudentService.listStudents} and
 *  {@code CollectionDetailsResponse.from}. */
class StudentTest {

    @Test
    void sortsByLastNameThenFirstName() {
        Student kowalskiAnna = student("Anna", "Kowalski");
        Student kowalskiJan = student("Jan", "Kowalski");
        Student adamski = student("Zenon", "Adamski");
        Student zawadzki = student("Adam", "Zawadzki");

        List<Student> students = new ArrayList<>(List.of(kowalskiJan, zawadzki, kowalskiAnna, adamski));
        students.sort(Student.byLastNameThenFirstName());

        assertEquals(List.of(adamski, kowalskiAnna, kowalskiJan, zawadzki), students);
    }

    @Test
    void usesPolishCollationNotRawCodePointOrder() {
        // Plain String.compareTo would put "Ł" (U+0141) after every ASCII letter, sorting
        // "Łukasiewicz" after "Zawadzki" - Polish collation places Ł right after L instead,
        // where a Polish reader actually expects it.
        Student lukasiewicz = student("Piotr", "Łukasiewicz");
        Student lisowski = student("Anna", "Lisowski");
        Student zawadzki = student("Adam", "Zawadzki");

        List<Student> students = new ArrayList<>(List.of(zawadzki, lukasiewicz, lisowski));
        students.sort(Student.byLastNameThenFirstName());

        assertEquals(List.of(lisowski, lukasiewicz, zawadzki), students);
    }

    private static Student student(String firstName, String lastName) {
        return new Student(firstName + "-" + lastName + "-id", firstName, lastName, BigDecimal.ZERO);
    }
}

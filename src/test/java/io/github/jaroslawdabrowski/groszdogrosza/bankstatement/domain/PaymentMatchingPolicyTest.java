package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.ParentRole;
import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentMatchingPolicyTest {

    private static final double THRESHOLD = 0.90;

    private static Student student(String id, String lastName) {
        return new Student(id, "Child", lastName, BigDecimal.ZERO);
    }

    private static Parent parent(String id, String studentId, String lastName, String expectedSenderName) {
        return new Parent(id, studentId, "First", lastName, "x@example.com", expectedSenderName, null,
                ParentRole.PARENT, null);
    }

    private static Parent treasurer(String id, String studentId, String expectedSenderName) {
        return new Parent(id, studentId, "First", "Last", "treasurer@example.com", expectedSenderName, null,
                ParentRole.TREASURER, null);
    }

    private static BankTransaction transaction(String senderName, String title) {
        return new BankTransaction(senderName, title, new BigDecimal("50.00"), LocalDate.now(), "ref-1");
    }

    // --- Step 0: treasurer's own account ---

    @Test
    void treasurerOwnAccountIsDetectedBySenderName() {
        List<Parent> parents = List.of(treasurer("t1", "s-treasurer-kid", "Jan Skarbnik"));

        assertTrue(PaymentMatchingPolicy.matchesTreasurerOwnAccount("Jan Skarbnik", parents, THRESHOLD));
    }

    @Test
    void unrelatedSenderIsNotTheTreasurer() {
        List<Parent> parents = List.of(treasurer("t1", "s-treasurer-kid", "Jan Skarbnik"));

        assertFalse(PaymentMatchingPolicy.matchesTreasurerOwnAccount("Anna Kowalska", parents, THRESHOLD));
    }

    @Test
    void treasurersOwnTransferIsNotCaughtByTheStudentSurnameTierDueToSurnameCollision() {
        // The treasurer's own child shares the treasurer's surname - a real risk this app's
        // matching order has to guard against (see PaymentMatchingPolicy's class javadoc).
        // The caller (BankStatementProcessingService) is expected to check
        // matchesTreasurerOwnAccount BEFORE calling match() at all; this test documents why.
        Student treasurersKid = student("s-treasurer-kid", "Skarbnik");
        List<Parent> parents = List.of(treasurer("t1", "s-treasurer-kid", "Jan Skarbnik"));

        assertTrue(PaymentMatchingPolicy.matchesTreasurerOwnAccount("Jan Skarbnik", parents, THRESHOLD));
        // If the caller forgot the step-0 check, this WOULD wrongly match the treasurer's own
        // outgoing transfer to their kid's piggy bank - exactly the bug step 0 prevents.
        Optional<MatchResult> wouldMatch = PaymentMatchingPolicy.match(
                transaction("Jan Skarbnik", "przelew"), List.of(treasurersKid), parents, THRESHOLD);
        assertTrue(wouldMatch.isPresent());
        assertEquals("s-treasurer-kid", wouldMatch.get().studentId());
    }

    // --- Tier 1: student's own surname, sender or title ---

    @Test
    void matchesStudentSurnameInSenderName() {
        // "Nowak" (gender-neutral in Polish) avoids the masculine/feminine surname suffix
        // trap ("Kowalski" vs "Kowalska") that a real whole-word comparison legitimately
        // does NOT bridge - that's intentional, not a bug, see the class javadoc on why
        // tier 1 is exact rather than fuzzy.
        List<Student> students = List.of(student("s1", "Nowak"));

        Optional<MatchResult> result = PaymentMatchingPolicy.match(
                transaction("Babcia Nowak", "wplata"), students, List.of(), THRESHOLD);

        assertTrue(result.isPresent());
        assertEquals("s1", result.get().studentId());
    }

    @Test
    void matchesStudentSurnameInTitle() {
        List<Student> students = List.of(student("s1", "Kowalski"));

        Optional<MatchResult> result = PaymentMatchingPolicy.match(
                transaction("Nieznana Osoba", "wplata za Kowalski klasa 2b"), students, List.of(), THRESHOLD);

        assertTrue(result.isPresent());
        assertEquals("s1", result.get().studentId());
    }

    @Test
    void studentSurnameMatchIsWholeWordNotSubstring() {
        List<Student> students = List.of(student("s1", "Kowal"));

        Optional<MatchResult> result = PaymentMatchingPolicy.match(
                transaction("Nieznana Osoba", "Kowalczyk platnosc"), students, List.of(), THRESHOLD);

        assertTrue(result.isEmpty());
    }

    @Test
    void ambiguousStudentSurnameFallsThroughToParentTier() {
        // Two students share a surname, but only one has a registered parent whose sender
        // name clearly matches - the ambiguous tier-1 hit must not block tier 2 from
        // resolving it.
        List<Student> students = List.of(student("s1", "Kowalski"), student("s2", "Kowalski"));
        List<Parent> parents = List.of(parent("p1", "s1", "Kowalski", "Jan Kowalski"));

        Optional<MatchResult> result = PaymentMatchingPolicy.match(
                transaction("Jan Kowalski", "wplata"), students, parents, THRESHOLD);

        assertTrue(result.isPresent());
        assertEquals("s1", result.get().studentId());
    }

    @Test
    void twoDifferentStudentsMatchedInSenderAndTitleAreAmbiguous() {
        List<Student> students = List.of(student("s1", "Kowalski"), student("s2", "Nowak"));

        Optional<MatchResult> result = PaymentMatchingPolicy.match(
                transaction("Kowalski", "za Nowak"), students, List.of(), THRESHOLD);

        assertTrue(result.isEmpty());
    }

    // --- Tier 2: parent fallback (fuzzy sender name, then title surname) ---

    @Test
    void fallsBackToFuzzyParentSenderNameMatch() {
        List<Parent> parents = List.of(parent("p1", "s1", "Kowalski", "Katarzyna Wisniewska"));

        // One transposed character - similarity stays >= 0.90.
        Optional<MatchResult> result = PaymentMatchingPolicy.match(
                transaction("Katarzyna Wisniewsak", "przelew"), List.of(), parents, THRESHOLD);

        assertTrue(result.isPresent());
        assertEquals("s1", result.get().studentId());
    }

    @Test
    void ambiguousParentSenderNameMatchesAreNotGuessed() {
        List<Parent> parents = List.of(
                parent("p1", "s1", "Kowalski", "Kowalski Jan"),
                parent("p2", "s2", "Kowalska", "Kowalska Jan"));

        Optional<MatchResult> result = PaymentMatchingPolicy.match(
                transaction("Kowalski Jan", "przelew"), List.of(), parents, 0.5);

        assertTrue(result.isEmpty());
    }

    @Test
    void fallsBackToParentSurnameInTitleWhenSenderDoesNotMatchAnyone() {
        List<Parent> parents = List.of(parent("p1", "s1", "Kowalski", "Jan Kowalski"));

        Optional<MatchResult> result = PaymentMatchingPolicy.match(
                transaction("Babcia Jana Kowalskiego", "wplata za Kowalski klasa 2b"), List.of(), parents, THRESHOLD);

        assertTrue(result.isPresent());
        assertEquals("s1", result.get().studentId());
    }

    @Test
    void nothingMatchesAnywhereIsUnmatched() {
        List<Student> students = List.of(student("s1", "Kowalski"));
        List<Parent> parents = List.of(parent("p1", "s1", "Kowalski", "Jan Kowalski"));

        Optional<MatchResult> result = PaymentMatchingPolicy.match(
                transaction("Zupelnie Inna Osoba", "cos innego"), students, parents, THRESHOLD);

        assertTrue(result.isEmpty());
    }

    @Test
    void emptyStudentsAndParentsNeverMatch() {
        Optional<MatchResult> result = PaymentMatchingPolicy.match(
                transaction("Jan Kowalski", "wplata"), List.of(), List.of(), THRESHOLD);

        assertTrue(result.isEmpty());
    }
}

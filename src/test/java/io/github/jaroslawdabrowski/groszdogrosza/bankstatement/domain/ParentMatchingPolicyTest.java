package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.ParentRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ParentMatchingPolicyTest {

    private static final double THRESHOLD = 0.90;

    private static Parent parent(String id, String expectedSenderName) {
        return new Parent(id, "First", "Last", "x@example.com", expectedSenderName, null, ParentRole.PARENT,
                BigDecimal.ZERO, null);
    }

    private static Parent parent(String id, String lastName, String expectedSenderName) {
        return new Parent(id, "First", lastName, "x@example.com", expectedSenderName, null, ParentRole.PARENT,
                BigDecimal.ZERO, null);
    }

    private static BankTransaction transaction(String senderName) {
        return new BankTransaction(senderName, "wplata", new BigDecimal("50.00"), LocalDate.now(), "ref-1");
    }

    private static BankTransaction transaction(String senderName, String title) {
        return new BankTransaction(senderName, title, new BigDecimal("50.00"), LocalDate.now(), "ref-1");
    }

    @Test
    void exactMatchIsAccepted() {
        List<Parent> parents = List.of(parent("p1", "Jan Kowalski"), parent("p2", "Anna Nowak"));

        Optional<MatchResult> result = ParentMatchingPolicy.match(transaction("Jan Kowalski"), parents, THRESHOLD);

        assertTrue(result.isPresent());
        assertEquals("p1", result.get().parentId());
        assertEquals(1.0, result.get().confidence());
    }

    @Test
    void matchIsCaseAndDiacriticInsensitive() {
        List<Parent> parents = List.of(parent("p1", "Łukasz Żurek"));

        Optional<MatchResult> result = ParentMatchingPolicy.match(transaction("LUKASZ ZUREK"), parents, THRESHOLD);

        assertTrue(result.isPresent());
        assertEquals("p1", result.get().parentId());
    }

    @Test
    void singleCharacterTypoStillMatchesAboveHighThreshold() {
        List<Parent> parents = List.of(parent("p1", "Katarzyna Wisniewska"));

        // One transposed/wrong character in a 20-character name - similarity stays >= 0.90.
        Optional<MatchResult> result = ParentMatchingPolicy.match(transaction("Katarzyna Wisniewsak"), parents, THRESHOLD);

        assertTrue(result.isPresent());
        assertEquals("p1", result.get().parentId());
    }

    @Test
    void unrelatedNameDoesNotMatch() {
        List<Parent> parents = List.of(parent("p1", "Jan Kowalski"));

        Optional<MatchResult> result = ParentMatchingPolicy.match(transaction("Zupełnie Inna Osoba"), parents, THRESHOLD);

        assertTrue(result.isEmpty());
    }

    @Test
    void twoSimilarCandidatesAboveThresholdAreAmbiguousAndNotMatched() {
        // Two different parents whose names are both close enough to the sender name to
        // individually clear the threshold - must not guess between them.
        List<Parent> parents = List.of(parent("p1", "Kowalski Jan"), parent("p2", "Kowalska Jan"));

        Optional<MatchResult> result = ParentMatchingPolicy.match(transaction("Kowalski Jan"), parents, 0.5);

        assertTrue(result.isEmpty());
    }

    @Test
    void emptyParentListNeverMatches() {
        Optional<MatchResult> result = ParentMatchingPolicy.match(transaction("Jan Kowalski"), List.of(), THRESHOLD);

        assertTrue(result.isEmpty());
    }

    @Test
    void fallsBackToTitleSurnameWhenSenderNameDoesNotMatch() {
        // A grandparent (or any account not printed under the parent's own name) pays in -
        // the sender name can't match anyone, but the transfer title carries the surname.
        List<Parent> parents = List.of(parent("p1", "Kowalski", "Jan Kowalski"));

        Optional<MatchResult> result = ParentMatchingPolicy.match(
                transaction("Babcia Jana Kowalskiego", "wplata za Kowalski klasa 2b"), parents, THRESHOLD);

        assertTrue(result.isPresent());
        assertEquals("p1", result.get().parentId());
    }

    @Test
    void titleFallbackIsNotUsedWhenSenderNameAlreadyMatches() {
        // The primary (fuzzy, sender-name) match wins outright - the title fallback should
        // never even be consulted when it isn't needed.
        List<Parent> parents = List.of(parent("p1", "Kowalski", "Jan Kowalski"));

        Optional<MatchResult> result = ParentMatchingPolicy.match(transaction("Jan Kowalski", "przelew"), parents, THRESHOLD);

        assertTrue(result.isPresent());
        assertEquals(1.0, result.get().confidence());
    }

    @Test
    void titleFallbackRequiresAWholeWordMatchNotASubstring() {
        // "Kowalczyk" must not match on a "Kowal" prefix, and a reference-code-looking
        // title shouldn't accidentally contain a real surname as a substring either.
        List<Parent> parents = List.of(parent("p1", "Kowal", "Jan Kowal"));

        Optional<MatchResult> result = ParentMatchingPolicy.match(
                transaction("Nieznana Osoba", "Kowalczyk platnosc"), parents, THRESHOLD);

        assertTrue(result.isEmpty());
    }

    @Test
    void titleFallbackIsAmbiguousWhenTwoParentsSurnamesAppearInTitle() {
        List<Parent> parents = List.of(parent("p1", "Kowalski", "Jan Kowalski"), parent("p2", "Nowak", "Anna Nowak"));

        Optional<MatchResult> result = ParentMatchingPolicy.match(
                transaction("Nieznana Osoba", "za Kowalski i Nowak"), parents, THRESHOLD);

        assertTrue(result.isEmpty());
    }

    @Test
    void titleFallbackDoesNothingWithoutATitle() {
        List<Parent> parents = List.of(parent("p1", "Kowalski", "Jan Kowalski"));

        Optional<MatchResult> result = ParentMatchingPolicy.match(transaction("Nieznana Osoba", ""), parents, THRESHOLD);

        assertTrue(result.isEmpty());
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Pure fuzzy name-matching logic between a bank transaction's sender name and the class's
 * parents. No framework dependency, no I/O - testable with plain JUnit.
 *
 * <p>This is the single riskiest piece of automation in the app: a wrong match means real
 * money gets credited to the wrong parent's piggy bank, fully automatically, with no human
 * in the loop. Two deliberate safety choices follow from that:
 * <ul>
 *   <li>The confidence threshold ({@code minConfidence}, sourced from
 *       {@code groszdogrosza.bankstatement.matching.min-confidence}, default 0.90) is
 *       high - a normalized Levenshtein similarity of 0.90 tolerates roughly one typo'd
 *       character in a ~10-character name, not much more.</li>
 *   <li>If <b>more than one</b> parent clears the threshold, this returns empty
 *       (ambiguous) rather than picking the "best" one - two similar surnames (e.g. two
 *       siblings' parents, or common Polish surnames like "Kowalski"/"Kowalska") must
 *       never be silently guessed between. An ambiguous or below-threshold transaction is
 *       left unmatched for the treasurer to book manually - see
 *       {@code bankstatement.application.BankStatementProcessingService}.</li>
 * </ul>
 *
 * <p><b>Title fallback</b>: the primary signal is always the sender name (who actually owns
 * the paying bank account); but that's often a grandparent, a spouse's separate account, or
 * a joint account printed under only one name, so it can legitimately fail to resemble any
 * parent's {@code expectedSenderName}. When the sender-name pass finds no unambiguous match,
 * this falls back to searching the transaction's free-text {@code title} (the public
 * overview page asks parents to put their child's surname there for exactly this reason) for
 * a parent's {@code lastName} as a whole word. The same "never guess between two candidates"
 * rule applies: if more than one parent's surname appears as a word in the title, or none
 * do, the transaction stays unmatched. This is an exact whole-word check, not fuzzy - a
 * title is short and often noisy (payment reference codes, "wpłata za"), so a typo-tolerant
 * search over it risks far more false positives than it's worth; sender-name matching stays
 * the only fuzzy path.
 */
public final class ParentMatchingPolicy {

    private ParentMatchingPolicy() {
    }

    public static Optional<MatchResult> match(BankTransaction transaction, List<Parent> parents, double minConfidence) {
        Optional<MatchResult> bySender = matchBySenderName(transaction, parents, minConfidence);
        return bySender.isPresent() ? bySender : matchByTitleSurname(transaction, parents);
    }

    private static Optional<MatchResult> matchBySenderName(BankTransaction transaction, List<Parent> parents, double minConfidence) {
        String normalizedSender = normalize(transaction.senderName());

        MatchResult best = null;
        int candidatesAtOrAboveThreshold = 0;

        for (Parent parent : parents) {
            double confidence = similarity(normalizedSender, normalize(parent.expectedSenderName()));
            if (confidence >= minConfidence) {
                candidatesAtOrAboveThreshold++;
                if (best == null || confidence > best.confidence()) {
                    best = new MatchResult(parent.id(), confidence);
                }
            }
        }

        if (candidatesAtOrAboveThreshold != 1) {
            // Zero matches, or more than one candidate above the threshold (ambiguous) -
            // never guess in either case.
            return Optional.empty();
        }
        return Optional.of(best);
    }

    private static Optional<MatchResult> matchByTitleSurname(BankTransaction transaction, List<Parent> parents) {
        if (transaction.title() == null || transaction.title().isBlank()) {
            return Optional.empty();
        }
        // Split on anything that isn't a letter, not just spaces - a title can carry
        // attached punctuation or a reference code ("Kowalski/OPF/AN/PL11...") that would
        // otherwise never equal a bare surname in a whole-word comparison.
        List<String> titleWords = List.of(normalize(transaction.title()).split("[^\\p{L}]+"));

        MatchResult found = null;
        int candidatesFound = 0;

        for (Parent parent : parents) {
            String normalizedLastName = normalize(parent.lastName());
            if (!normalizedLastName.isBlank() && titleWords.contains(normalizedLastName)) {
                candidatesFound++;
                found = new MatchResult(parent.id(), 1.0);
            }
        }

        return candidatesFound == 1 ? Optional.of(found) : Optional.empty();
    }

    /** Lowercase, strip Polish diacritics, collapse whitespace - keeps comparisons robust
     *  to how a bank happens to capitalize/space a printed name. */
    static String normalize(String name) {
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        String withoutDiacritics = decomposed.replaceAll("\\p{M}", "");
        return withoutDiacritics.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** 1.0 = identical, 0.0 = completely different; based on Levenshtein edit distance
     *  normalized by the longer string's length. */
    static double similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        int maxLength = Math.max(a.length(), b.length());
        if (maxLength == 0) {
            return 1.0;
        }
        int distance = levenshteinDistance(a, b);
        return 1.0 - ((double) distance / maxLength);
    }

    private static int levenshteinDistance(String a, String b) {
        int[] previousRow = new int[b.length() + 1];
        int[] currentRow = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            previousRow[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            currentRow[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                currentRow[j] = Math.min(Math.min(currentRow[j - 1] + 1, previousRow[j] + 1), previousRow[j - 1] + cost);
            }
            System.arraycopy(currentRow, 0, previousRow, 0, currentRow.length);
        }

        return previousRow[b.length()];
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.ParentRole;
import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves an incoming bank transaction to a {@code Student} (whose piggy bank the money
 * actually belongs to - see {@code Student}'s javadoc for why the unit moved off
 * {@code Parent}). This is the single riskiest piece of automation in the app: a wrong match
 * means real money gets credited to the wrong family's child, fully automatically, with no
 * human in the loop. Three tiers, tried in order, each independently safe:
 *
 * <ol>
 *   <li><b>Step 0 - the treasurer's own account is never booked</b>
 *       ({@link #matchesTreasurerOwnAccount}), checked by the caller BEFORE anything below
 *       runs. The mailbox being polled belongs to the treasurer, so the statement naturally
 *       contains their own outgoing/internal transfers, not just other families paying in.
 *       This has to run first, not after: the treasurer's own child is also a
 *       {@code Student} in this model (see {@code Student} javadoc), and their surname is
 *       very likely the same as the treasurer's own - without this ordering, a treasurer's
 *       own outgoing transfer could accidentally get caught by tier 1 below (matching their
 *       own kid's surname) and get wrongly credited as a contribution instead of ignored.</li>
 *   <li><b>Tier 1 - a student's own surname</b> ({@link #matchByStudentSurname}): a whole
 *       word, in either the sender name or the transfer title, matching a
 *       {@code Student.lastName}. Exact, not fuzzy - both fields are short and can carry
 *       unrelated tokens (payment reference codes, "wpłata za", a payer's own name), so a
 *       typo-tolerant search across them risks more false positives than it's worth. If more
 *       than one distinct student's surname turns up this way, that's ambiguous and this
 *       tier yields nothing (falls through to tier 2), never a guess.</li>
 *   <li><b>Tier 2 - the registered parent fallback</b> ({@link #matchByParent}): the
 *       original matching logic - fuzzy (Levenshtein) comparison of the sender name against
 *       each parent's {@code expectedSenderName}, or (if that's empty/ambiguous) an exact
 *       whole-word surname match against the title - resolved to that parent's
 *       {@code studentId}. Covers a transfer sent from an account not printed under the
 *       paying family's own surname (a grandparent's account, a spouse's separate account).</li>
 * </ol>
 *
 * <p>Every ambiguity rule below is "more than one candidate clears the bar → give up, don't
 * guess" - two similar surnames (siblings' families, common Polish surnames) must never be
 * silently picked between. An unmatched or ambiguous transaction is left for the treasurer
 * to book manually - see {@code bankstatement.application.BankStatementProcessingService}.
 */
public final class PaymentMatchingPolicy {

    private PaymentMatchingPolicy() {
    }

    /** Step 0 - must be checked by the caller before {@link #match}. */
    public static boolean matchesTreasurerOwnAccount(String senderName, List<Parent> parents, double minConfidence) {
        String normalizedSender = normalize(senderName);
        return parents.stream()
                .filter(parent -> parent.role() == ParentRole.TREASURER)
                .anyMatch(parent -> similarity(normalizedSender, normalize(parent.expectedSenderName())) >= minConfidence);
    }

    public static Optional<MatchResult> match(
            BankTransaction transaction, List<Student> students, List<Parent> parents, double minConfidence) {
        Optional<MatchResult> byStudentSurname = matchByStudentSurname(transaction, students);
        if (byStudentSurname.isPresent()) {
            return byStudentSurname;
        }
        return matchByParent(transaction, parents, minConfidence);
    }

    private static Optional<MatchResult> matchByStudentSurname(BankTransaction transaction, List<Student> students) {
        Set<String> words = new HashSet<>(wordsOf(transaction.senderName()));
        words.addAll(wordsOf(transaction.title()));

        Set<String> matchedStudentIds = new HashSet<>();
        for (Student student : students) {
            String normalizedLastName = normalize(student.lastName());
            if (!normalizedLastName.isBlank() && words.contains(normalizedLastName)) {
                matchedStudentIds.add(student.id());
            }
        }

        if (matchedStudentIds.size() != 1) {
            // Zero or ambiguous - fall through to the parent-fallback tier rather than
            // stopping here; a parent match might still resolve unambiguously.
            return Optional.empty();
        }
        return Optional.of(new MatchResult(matchedStudentIds.iterator().next(), 1.0));
    }

    private static Optional<MatchResult> matchByParent(BankTransaction transaction, List<Parent> parents, double minConfidence) {
        Optional<MatchResult> bySenderName = matchByParentSenderName(transaction, parents, minConfidence);
        if (bySenderName.isPresent()) {
            return bySenderName;
        }
        return matchByParentTitleSurname(transaction, parents);
    }

    private static Optional<MatchResult> matchByParentSenderName(BankTransaction transaction, List<Parent> parents, double minConfidence) {
        String normalizedSender = normalize(transaction.senderName());

        Parent best = null;
        double bestConfidence = -1;
        int candidatesAtOrAboveThreshold = 0;

        for (Parent parent : parents) {
            double confidence = similarity(normalizedSender, normalize(parent.expectedSenderName()));
            if (confidence >= minConfidence) {
                candidatesAtOrAboveThreshold++;
                if (confidence > bestConfidence) {
                    best = parent;
                    bestConfidence = confidence;
                }
            }
        }

        if (candidatesAtOrAboveThreshold != 1) {
            // Zero matches, or more than one candidate above the threshold (ambiguous) -
            // never guess in either case.
            return Optional.empty();
        }
        if (best.studentId() == null) {
            // A parent record with no student linked yet - nothing to credit.
            return Optional.empty();
        }
        return Optional.of(new MatchResult(best.studentId(), bestConfidence));
    }

    private static Optional<MatchResult> matchByParentTitleSurname(BankTransaction transaction, List<Parent> parents) {
        if (transaction.title() == null || transaction.title().isBlank()) {
            return Optional.empty();
        }
        Set<String> titleWords = wordsOf(transaction.title());

        Set<String> matchedStudentIds = new HashSet<>();
        for (Parent parent : parents) {
            String normalizedLastName = normalize(parent.lastName());
            if (!normalizedLastName.isBlank() && titleWords.contains(normalizedLastName) && parent.studentId() != null) {
                matchedStudentIds.add(parent.studentId());
            }
        }

        return matchedStudentIds.size() == 1
                ? Optional.of(new MatchResult(matchedStudentIds.iterator().next(), 1.0))
                : Optional.empty();
    }

    /** Split on anything that isn't a letter, not just spaces - a sender name or title can
     *  carry attached punctuation or a reference code ("Kowalski/OPF/AN/PL11...") that would
     *  otherwise never equal a bare surname in a whole-word comparison. */
    private static Set<String> wordsOf(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Set.of(normalize(text).split("[^\\p{L}]+"));
    }

    /** Lowercase, strip Polish diacritics, collapse whitespace - keeps comparisons robust
     *  to how a bank happens to capitalize/space a printed name. */
    static String normalize(String name) {
        if (name == null) {
            return "";
        }
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

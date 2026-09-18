package io.github.jaroslawdabrowski.groszdogrosza.student.domain;

import java.math.BigDecimal;

/**
 * A child in the class - the unit collections and piggy banks are actually tracked against
 * (not the parent, who is just a login/contact attached to a student - see
 * {@code parent.domain.Parent.studentId}). A collection with 18 students open means 18
 * {@code ContributionRequirement}s, one per student, regardless of how many of them have a
 * parent account registered at all.
 *
 * <p>This replaces {@code Parent.piggyBankBalance} from the original data model: the
 * treasurer's own worked example ("Kowalski wpłacił... trafia do skarbonki Kowalskiego")
 * turned out to mean the *child's* running balance, not the paying adult's - a parent who
 * remarries or a sibling who pays from a different account should still land in the same
 * pot. Students never log in and have no email/Cognito account.
 */
public record Student(
        String id,
        String firstName,
        String lastName,
        BigDecimal piggyBankBalance) {

    public Student {
        if (piggyBankBalance == null) {
            piggyBankBalance = BigDecimal.ZERO;
        }
        if (piggyBankBalance.signum() < 0) {
            throw new IllegalArgumentException("piggyBankBalance cannot be negative: " + piggyBankBalance);
        }
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}

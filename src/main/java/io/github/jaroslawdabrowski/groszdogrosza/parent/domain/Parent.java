package io.github.jaroslawdabrowski.groszdogrosza.parent.domain;

import java.math.BigDecimal;

/**
 * A parent of a child in the class - the unit the treasurer tracks money against.
 *
 * @param expectedSenderName how the parent's name is expected to appear as the sender on
 *                           a bank transfer (e.g. "Jan Kowalski") - used by
 *                           {@code bankstatement.domain.ParentMatchingPolicy} to match
 *                           incoming transactions. Kept separate from first/last name so a
 *                           parent whose transfers arrive under a different name (spouse's
 *                           account, maiden name, etc.) can still be matched correctly.
 * @param cognitoSubjectId   null until the parent has actually logged in once and been
 *                           linked to a Cognito/Keycloak identity - most parents never need
 *                           an account at all, only the treasurer does.
 * @param role               {@link ParentRole#TREASURER} or {@link ParentRole#PARENT} - see
 *                           {@code platform.security.AuthorizationSupport} for how this
 *                           drives every authorization decision in the app.
 * @param piggyBankBalance   money this parent has overpaid on past collections, available to
 *                           be applied automatically to future ones. Never negative.
 */
public record Parent(
        String id,
        String firstName,
        String lastName,
        String email,
        String expectedSenderName,
        String cognitoSubjectId,
        ParentRole role,
        BigDecimal piggyBankBalance) {

    public Parent {
        if (piggyBankBalance == null) {
            piggyBankBalance = BigDecimal.ZERO;
        }
        if (piggyBankBalance.signum() < 0) {
            throw new IllegalArgumentException("piggyBankBalance cannot be negative: " + piggyBankBalance);
        }
        if (role == null) {
            role = ParentRole.PARENT;
        }
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}

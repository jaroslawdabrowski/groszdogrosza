package io.github.jaroslawdabrowski.groszdogrosza.parent.domain;

/**
 * A parent (or guardian) of a {@code student.domain.Student} - the login/contact record, not
 * the money-tracking unit anymore (that moved to {@code Student.piggyBankBalance} - see its
 * javadoc for why). A student has 0, 1 or 2 parents; a parent belongs to exactly one student
 * ({@link #studentId()}) - the relationship is deliberately owned by the student side (a
 * parent with two children in the class needs two separate {@code Parent} records, one per
 * student - an accepted simplification at this app's one-class scale).
 *
 * @param studentId          the student this parent belongs to. Always set in practice - a
 *                           {@code Parent} is only ever created via
 *                           {@code POST /api/students/{id}/parents}, never standalone.
 * @param expectedSenderName how the parent's name is expected to appear as the sender on a
 *                           bank transfer (e.g. "Jan Kowalski") - used by
 *                           {@code bankstatement.domain.PaymentMatchingPolicy} as the
 *                           fallback match when the transfer doesn't already carry the
 *                           student's own surname. Kept separate from first/last name so a
 *                           parent whose transfers arrive under a different name (spouse's
 *                           account, maiden name, etc.) can still be matched correctly.
 * @param cognitoSubjectId   null until the parent has actually logged in once and been
 *                           linked to a Cognito/Keycloak identity - most parents never need
 *                           an account at all, only the treasurer does.
 * @param role               {@link ParentRole#TREASURER} or {@link ParentRole#PARENT} - see
 *                           {@code platform.security.AuthorizationSupport} for how this
 *                           drives every authorization decision in the app.
 * @param paymentInfo        null unless the treasurer has configured it - see
 *                           {@link PaymentInfo}. Shown on the public, unauthenticated
 *                           collection overview page so anyone can pay in.
 */
public record Parent(
        String id,
        String studentId,
        String firstName,
        String lastName,
        String email,
        String expectedSenderName,
        String cognitoSubjectId,
        ParentRole role,
        PaymentInfo paymentInfo) {

    public Parent {
        if (role == null) {
            role = ParentRole.PARENT;
        }
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}

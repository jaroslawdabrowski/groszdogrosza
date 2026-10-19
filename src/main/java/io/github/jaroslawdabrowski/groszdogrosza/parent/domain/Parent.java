package io.github.jaroslawdabrowski.groszdogrosza.parent.domain;

/**
 * A parent (or guardian) of a {@code student.domain.Student} - the login/contact record, not
 * the money-tracking unit (that's {@code Student.piggyBankBalance}). A student has 0, 1 or 2
 * parents; a parent record belongs to exactly one student ({@link #studentId()}). A person
 * with children in several classes (or two children in one class) has one record per child,
 * all with the same email - one login sees all of them (see docs/MULTI-CLASS.md).
 *
 * @param classId            the class of {@link #studentId()} - copied onto the record so
 *                           class-scoped reads can filter on it directly.
 * @param studentId          the student this parent belongs to. Always set in practice - a
 *                           {@code Parent} is only ever created via
 *                           {@code POST /api/classes/{classId}/students/{id}/parents}.
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
 * @param role               legacy, from the one-class version: who was the treasurer. Kept
 *                           and written back unchanged so the previous version of the app
 *                           still works on migrated data (rollback), but no longer used for
 *                           any authorization - treasurers are per class now
 *                           ({@code schoolclass}). Still used by the bank-statement matching
 *                           to recognise the treasurer's own outgoing transfers.
 * @param paymentInfo        legacy, same reason as {@code role}: payment details live on the
 *                           class now ({@code schoolclass.domain.SchoolClass}).
 */
public record Parent(
        String id,
        String classId,
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

package io.github.jaroslawdabrowski.groszdogrosza.student.port.in;

/**
 * Treasurer-only. Cascades to delete every {@code Parent} linked to this student (a parent
 * cannot meaningfully exist without their student in this model - see
 * {@code parent.domain.Parent.studentId}) but does NOT delete their Cognito login accounts,
 * which need manual cleanup - matches {@code parent.port.in.DeleteParentUseCase}'s own
 * "does not cascade to Cognito" note. Does NOT cascade to
 * {@code ContributionRequirement}/{@code Contribution}/{@code LedgerEntry} history for the
 * same reason neither does {@code DeleteParentUseCase} - no referential-integrity layer in
 * this app, existing records simply keep referencing an id that no longer resolves to a name.
 */
public interface DeleteStudentUseCase {

    void deleteStudent(String studentId);
}

package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

/**
 * The reverse of {@link RemoveStudentFromCollectionUseCase} - puts a student who wasn't
 * (or no longer is) part of an ACTIVE collection back into it. Goes through the exact same
 * "add a requirement, sweep whatever the current piggy bank balance covers" path
 * {@code CreateCollectionUseCase} uses per included student, not a bare zeroed requirement -
 * see {@code CollectionService}'s shared implementation for why that matters (a real piggy
 * bank debit, ledger entry, and Contribution, not a silent discount with nothing to show
 * for it).
 */
public interface AddStudentToCollectionUseCase {

    void addStudentToCollection(String collectionId, String studentId);
}

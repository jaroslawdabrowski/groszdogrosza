package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

/**
 * Takes a student out of an ACTIVE collection - e.g. a class trip a student can no longer
 * attend (illness, moved away). Deletes that student's {@code ContributionRequirement} and
 * every {@code Contribution} they made to this collection (so they stop showing up
 * anywhere in this collection's breakdown, and {@code SettlementPolicy} never sees their
 * money when the collection is later settled), and refunds whatever they'd already paid
 * back to their piggy bank - see {@code CollectionService.removeStudentFromCollection} for
 * why this has to delete rather than just zero out the requirement.
 */
public interface RemoveStudentFromCollectionUseCase {

    void removeStudentFromCollection(String collectionId, String studentId);
}

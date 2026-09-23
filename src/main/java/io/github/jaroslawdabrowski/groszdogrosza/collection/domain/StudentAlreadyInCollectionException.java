package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

/**
 * Thrown by {@code AddStudentToCollectionUseCase} when the student already has a
 * {@code ContributionRequirement} for this collection - adding them again would create a
 * second requirement (and, if their piggy bank still covers it, sweep the same money in
 * twice). Not expected to happen through the normal UI (a student already in the collection
 * is shown with a "remove" action, not an "add" one), but guarded explicitly rather than
 * silently overwriting.
 */
public class StudentAlreadyInCollectionException extends RuntimeException {

    public StudentAlreadyInCollectionException(String collectionId, String studentId) {
        super("Student " + studentId + " is already part of collection " + collectionId);
    }
}

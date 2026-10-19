package io.github.jaroslawdabrowski.groszdogrosza.collection.port.out;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Contribution;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import java.util.List;
import java.util.Optional;

public interface CollectionRepositoryPort {

    Collection saveCollection(Collection collection);

    /** Empty when the collection doesn't exist OR belongs to another class. Requirements,
     *  contributions and attachments are only ever reached after this check. */
    Optional<Collection> findCollectionById(String classId, String collectionId);

    List<Collection> findAllCollections(String classId);

    ContributionRequirement saveRequirement(ContributionRequirement requirement);

    List<ContributionRequirement> findRequirementsByCollectionId(String collectionId);

    Optional<ContributionRequirement> findRequirement(String collectionId, String studentId);

    /** Across every ACTIVE collection, requirements for this student that are not yet PAID/OVERPAID. */
    List<ContributionRequirement> findActivePendingRequirementsForStudent(String classId, String studentId);

    /** Used by {@code RemoveStudentFromCollectionUseCase} - a student taken out of a
     *  collection stops having a requirement in it at all, not just a zeroed one. */
    void deleteRequirement(String collectionId, String studentId);

    Contribution saveContribution(Contribution contribution);

    List<Contribution> findContributionsByCollectionId(String collectionId);

    /** Used by {@code RemoveStudentFromCollectionUseCase} - every contribution the removed
     *  student made to this collection is deleted (after being refunded to their piggy
     *  bank), so {@code SettlementPolicy} never sees it when the collection is later
     *  settled. */
    void deleteContribution(String collectionId, String contributionId);
}

package io.github.jaroslawdabrowski.groszdogrosza.collection.port.out;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Contribution;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import java.util.List;
import java.util.Optional;

public interface CollectionRepositoryPort {

    Collection saveCollection(Collection collection);

    Optional<Collection> findCollectionById(String collectionId);

    List<Collection> findAllCollections();

    ContributionRequirement saveRequirement(ContributionRequirement requirement);

    List<ContributionRequirement> findRequirementsByCollectionId(String collectionId);

    Optional<ContributionRequirement> findRequirement(String collectionId, String parentId);

    /** Across every ACTIVE collection, requirements for this parent that are not yet PAID/OVERPAID. */
    List<ContributionRequirement> findActivePendingRequirementsForParent(String parentId);

    Contribution saveContribution(Contribution contribution);

    List<Contribution> findContributionsByCollectionId(String collectionId);
}

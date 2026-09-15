package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Contribution;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import java.util.List;
import java.util.Optional;

public interface GetCollectionUseCase {

    Optional<CollectionDetails> getCollection(String collectionId);

    record CollectionDetails(
            Collection collection,
            List<ContributionRequirement> requirements,
            List<Contribution> contributions) {
    }
}

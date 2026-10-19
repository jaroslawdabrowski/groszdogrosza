package io.github.jaroslawdabrowski.groszdogrosza.platform.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web.CollectionProgressResponse;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.CollectionStatus;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.ListCollectionsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.SchoolClass;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/** Builds a class's {@link PublicOverviewResponse}. */
@ApplicationScoped
public class ClassOverviewAssembler {

    @Inject
    ListCollectionsUseCase listCollectionsUseCase;

    @Inject
    GetCollectionUseCase getCollectionUseCase;

    /**
     * @param myStudentId the caller's own child in THIS class, to show "your child owes / has
     *                    paid" on each collection - null for anyone who isn't a parent in the
     *                    class (anonymous visitors and members of other classes alike)
     */
    public PublicOverviewResponse build(SchoolClass schoolClass, String myStudentId) {
        String classId = schoolClass.id();
        List<CollectionProgressResponse> activeCollections = listCollectionsUseCase.listCollections(classId).stream()
                .filter(collection -> collection.status() == CollectionStatus.ACTIVE)
                .sorted(java.util.Comparator.comparing(Collection::createdAt))
                .map(Collection::id)
                .flatMap(id -> getCollectionUseCase.getCollection(classId, id).stream())
                .map(details -> CollectionProgressResponse.from(details, myStudentId))
                .toList();
        return new PublicOverviewResponse(schoolClass.name(),
                PublicOverviewResponse.PaymentInfoResponse.from(schoolClass.paymentInfo()), activeCollections);
    }
}

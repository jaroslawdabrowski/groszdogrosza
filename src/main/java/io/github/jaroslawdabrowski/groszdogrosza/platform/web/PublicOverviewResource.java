package io.github.jaroslawdabrowski.groszdogrosza.platform.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web.CollectionProgressResponse;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.CollectionStatus;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.ListCollectionsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.GetTreasurerPaymentInfoUseCase;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * Deliberately unauthenticated (see {@code quarkus.http.auth.permission.public.paths} in
 * application.properties, which must include {@code /api/public/*}) - the user explicitly
 * wants the current collection's progress and how to pay to be visible to anyone, without
 * logging in, so parents (and anyone else, e.g. a grandparent) can pay in and check status
 * without an account. Only aggregate, per-collection data - never per-parent names/amounts
 * (that stays behind login, see {@code LedgerResource}/{@code CollectionResource.get}'s
 * treasurer-only branch) - and only the treasurer's payment info, never any other parent's
 * personal data.
 */
@Path("/api/public")
@Produces(MediaType.APPLICATION_JSON)
public class PublicOverviewResource {

    @Inject
    ListCollectionsUseCase listCollectionsUseCase;

    @Inject
    GetCollectionUseCase getCollectionUseCase;

    @Inject
    GetTreasurerPaymentInfoUseCase getTreasurerPaymentInfoUseCase;

    @GET
    @Path("/overview")
    public PublicOverviewResponse overview() {
        List<CollectionProgressResponse> activeCollections = listCollectionsUseCase.listCollections().stream()
                .filter(collection -> collection.status() == CollectionStatus.ACTIVE)
                .map(Collection::id)
                .flatMap(id -> getCollectionUseCase.getCollection(id).stream())
                .map(CollectionProgressResponse::from)
                .toList();

        PublicOverviewResponse.PaymentInfoResponse paymentInfo = getTreasurerPaymentInfoUseCase.getTreasurerPaymentInfo()
                .map(PublicOverviewResponse.PaymentInfoResponse::from)
                .orElse(null);

        return new PublicOverviewResponse(paymentInfo, activeCollections);
    }
}

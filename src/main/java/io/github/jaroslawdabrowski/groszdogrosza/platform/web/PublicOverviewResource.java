package io.github.jaroslawdabrowski.groszdogrosza.platform.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web.CollectionProgressResponse;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.CollectionStatus;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.ListCollectionsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.GetTreasurerPaymentInfoUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.AuthorizationSupport;
import io.quarkus.security.identity.SecurityIdentity;
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
 *
 * <p>One exception, and it's still only ever the caller's OWN data: {@code authInterceptor}
 * on the frontend attaches the bearer token to every {@code /api/*} call - including this
 * unauthenticated one - whenever the visitor happens to be logged in, so {@link
 * #overview()} still resolves a real {@link SecurityIdentity} for a logged-in parent even
 * though the endpoint doesn't require one (an anonymous visitor just gets an identity with
 * no JWT principal, same as always). That's used purely to compute each collection's {@code
 * myStudentStatus} - see {@code CollectionResponse}'s javadoc - never to branch what
 * aggregate data is returned.
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

    @Inject
    AuthorizationSupport authorizationSupport;

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/overview")
    public PublicOverviewResponse overview() {
        String myStudentId = authorizationSupport.currentParent(identity).map(Parent::studentId).orElse(null);
        List<CollectionProgressResponse> activeCollections = listCollectionsUseCase.listCollections().stream()
                .filter(collection -> collection.status() == CollectionStatus.ACTIVE)
                .map(Collection::id)
                .flatMap(id -> getCollectionUseCase.getCollection(id).stream())
                .map(details -> CollectionProgressResponse.from(details, myStudentId))
                .toList();

        PublicOverviewResponse.PaymentInfoResponse paymentInfo = getTreasurerPaymentInfoUseCase.getTreasurerPaymentInfo()
                .map(PublicOverviewResponse.PaymentInfoResponse::from)
                .orElse(null);

        return new PublicOverviewResponse(paymentInfo, activeCollections);
    }
}

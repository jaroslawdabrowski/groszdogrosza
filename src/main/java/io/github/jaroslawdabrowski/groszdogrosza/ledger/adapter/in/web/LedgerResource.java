package io.github.jaroslawdabrowski.groszdogrosza.ledger.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.GetLedgerForParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.GetParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.AuthorizationSupport;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * A parent's ledger is exactly as sensitive as their balance itself - see
 * {@code ParentResource.get} and {@code AuthorizationSupport} for the same self-or-treasurer
 * rule applied here.
 */
@Path("/api/parents/{parentId}/ledger")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class LedgerResource {

    @Inject
    GetLedgerForParentUseCase getLedgerForParentUseCase;

    @Inject
    GetParentUseCase getParentUseCase;

    @Inject
    AuthorizationSupport authorizationSupport;

    @Inject
    SecurityIdentity identity;

    @GET
    public List<LedgerEntryResponse> get(@PathParam("parentId") String parentId) {
        Parent parent = getParentUseCase.getParent(parentId)
                .orElseThrow(() -> new NotFoundException("No such parent: " + parentId));
        authorizationSupport.requireSelfOrTreasurer(identity, parent.email());
        return getLedgerForParentUseCase.getLedgerFor(parentId).stream().map(LedgerEntryResponse::from).toList();
    }
}

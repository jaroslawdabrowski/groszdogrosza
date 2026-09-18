package io.github.jaroslawdabrowski.groszdogrosza.ledger.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.GetFullLedgerUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.AuthorizationSupport;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 * Treasurer-only "log wszystkich transakcji" - every parent's ledger entries in one feed,
 * unlike {@code LedgerResource} which only ever returns one parent's own (self-or-treasurer).
 */
@Path("/api/ledger")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class GlobalLedgerResource {

    @Inject
    GetFullLedgerUseCase getFullLedgerUseCase;

    @Inject
    ListParentsUseCase listParentsUseCase;

    @Inject
    AuthorizationSupport authorizationSupport;

    @Inject
    SecurityIdentity identity;

    @GET
    public List<GlobalLedgerEntryResponse> get() {
        authorizationSupport.requireTreasurer(identity);
        Map<String, String> namesByParentId = listParentsUseCase.listParents().stream()
                .collect(java.util.stream.Collectors.toMap(Parent::id, Parent::fullName, (a, b) -> a));
        return getFullLedgerUseCase.getFullLedger().stream()
                .map(entry -> GlobalLedgerEntryResponse.from(entry,
                        namesByParentId.getOrDefault(entry.parentId(), entry.parentId())))
                .toList();
    }
}

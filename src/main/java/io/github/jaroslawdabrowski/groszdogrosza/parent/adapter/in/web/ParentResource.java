package io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.ParentRole;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreateCognitoAccountUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreateParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreditPiggyBankManuallyUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.GetParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ResendCognitoInvitationUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.UpdatePaymentInfoUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.AuthorizationSupport;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * {@code list}/{@code create}/the piggy-bank credit endpoint are treasurer-only - see
 * {@code AuthorizationSupport} for why: they either enumerate every family's data
 * (id-harvesting risk), create real accounts, or move real money. {@code get} is available
 * to a parent for their own record too (self-service, checked against the OIDC
 * {@code email} claim), matching {@code LedgerResource}.
 *
 * <p>Roles are checked explicitly here (not via {@code @RolesAllowed}) because the source of
 * truth for "who's the treasurer" is {@link Parent#role()} in this app's own data, not an
 * identity-provider role/group claim - see {@code ParentRole}.
 */
@Path("/api/parents")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ParentResource {

    @Inject
    CreateParentUseCase createParentUseCase;

    @Inject
    GetParentUseCase getParentUseCase;

    @Inject
    ListParentsUseCase listParentsUseCase;

    @Inject
    CreditPiggyBankManuallyUseCase creditPiggyBankManuallyUseCase;

    @Inject
    UpdatePaymentInfoUseCase updatePaymentInfoUseCase;

    @Inject
    CreateCognitoAccountUseCase createCognitoAccountUseCase;

    @Inject
    ResendCognitoInvitationUseCase resendCognitoInvitationUseCase;

    @Inject
    AuthorizationSupport authorizationSupport;

    @Inject
    SecurityIdentity identity;

    @GET
    public List<ParentResponse> list() {
        authorizationSupport.requireTreasurer(identity);
        return listParentsUseCase.listParents().stream().map(ParentResponse::from).toList();
    }

    @POST
    public ParentResponse create(CreateParentRequest request) {
        authorizationSupport.requireTreasurer(identity);
        ParentRole role = request.role() == null ? ParentRole.PARENT : ParentRole.valueOf(request.role());
        return ParentResponse.from(createParentUseCase.createParent(
                request.firstName(), request.lastName(), request.email(), request.expectedSenderName(), role));
    }

    /**
     * Lets the frontend discover the current caller's own role/id right after login (to
     * decide whether to show treasurer-only UI, and which collection view to render) without
     * needing to know their parentId up front.
     */
    @GET
    @Path("/me")
    public ParentResponse me() {
        return authorizationSupport.currentParent(identity).map(ParentResponse::from)
                .orElseThrow(() -> new NotFoundException(
                        "No parent record matches the current account's email yet - ask the treasurer to create one"));
    }

    @GET
    @Path("/{id}")
    public ParentResponse get(@PathParam("id") String id) {
        Parent parent = getParentUseCase.getParent(id).orElseThrow(() -> new NotFoundException("No such parent: " + id));
        authorizationSupport.requireSelfOrTreasurer(identity, parent.email());
        return ParentResponse.from(parent);
    }

    @POST
    @Path("/{id}/piggy-bank/credit")
    public ParentResponse creditPiggyBank(@PathParam("id") String id, CreditPiggyBankRequest request) {
        authorizationSupport.requireTreasurer(identity);
        return ParentResponse.from(creditPiggyBankManuallyUseCase.creditPiggyBankManually(id, request.amount()));
    }

    /**
     * Treasurer-only. In practice only ever called for the treasurer's own record (that's
     * the only one shown on the public overview page - see {@code PublicOverviewResource})
     * but not restricted to self, since only the treasurer can call this at all.
     */
    @PUT
    @Path("/{id}/payment-info")
    public ParentResponse updatePaymentInfo(@PathParam("id") String id, UpdatePaymentInfoRequest request) {
        authorizationSupport.requireTreasurer(identity);
        return ParentResponse.from(
                updatePaymentInfoUseCase.updatePaymentInfo(id, request.bankAccountNumber(), request.blikPhoneNumber()));
    }

    /** Creates this parent's login account - Cognito generates a temporary password and
     *  emails it via its own built-in invitation message (see
     *  {@code CognitoAccountManagementAdapter}). 409 if one already exists for this email -
     *  see {@code resendCognitoInvitation} for that case. */
    @POST
    @Path("/{id}/cognito-account")
    public Response createCognitoAccount(@PathParam("id") String id) {
        authorizationSupport.requireTreasurer(identity);
        createCognitoAccountUseCase.createCognitoAccount(id);
        return Response.noContent().build();
    }

    /** "I didn't get the invitation e-mail" - re-sends it with a freshly generated
     *  temporary password. Only works while the account is still unconfirmed. */
    @POST
    @Path("/{id}/cognito-account/resend")
    public Response resendCognitoInvitation(@PathParam("id") String id) {
        authorizationSupport.requireTreasurer(identity);
        resendCognitoInvitationUseCase.resendCognitoInvitation(id);
        return Response.noContent().build();
    }
}

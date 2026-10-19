package io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreateCognitoAccountUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.DeleteParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.GetParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ResendCognitoInvitationUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.UpdateParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.ClassContext;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** A class's parent records. Class membership is checked by ClassAccessFilter; editing is
 *  treasurer-only, reading one record is the treasurer or that parent themselves. */
@Path("/api/classes/{classId}/parents")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ParentResource {

    @Inject
    GetParentUseCase getParentUseCase;

    @Inject
    UpdateParentUseCase updateParentUseCase;

    @Inject
    DeleteParentUseCase deleteParentUseCase;

    @Inject
    CreateCognitoAccountUseCase createCognitoAccountUseCase;

    @Inject
    ResendCognitoInvitationUseCase resendCognitoInvitationUseCase;

    @Inject
    ClassContext classContext;

    @GET
    @Path("/{id}")
    public ParentResponse get(@PathParam("id") String id) {
        if (!classContext.isTreasurer() && !classContext.membership().parentIds().contains(id)) {
            throw new NotFoundException("No such parent: " + id);
        }
        Parent parent = getParentUseCase.getParent(classContext.classId(), id)
                .orElseThrow(() -> new NotFoundException("No such parent: " + id));
        return ParentResponse.from(parent);
    }

    @PUT
    @Path("/{id}")
    public ParentResponse update(@PathParam("id") String id, UpdateParentRequest request) {
        classContext.requireTreasurer();
        return ParentResponse.from(updateParentUseCase.updateParent(classContext.classId(),
                id, request.firstName(), request.lastName(), request.email(), request.expectedSenderName()));
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        classContext.requireTreasurer();
        deleteParentUseCase.deleteParent(classContext.classId(), id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/cognito-account")
    public Response createCognitoAccount(@PathParam("id") String id) {
        classContext.requireTreasurer();
        createCognitoAccountUseCase.createCognitoAccount(classContext.classId(), id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/cognito-account/resend")
    public Response resendCognitoInvitation(@PathParam("id") String id) {
        classContext.requireTreasurer();
        resendCognitoInvitationUseCase.resendCognitoInvitation(classContext.classId(), id);
        return Response.noContent().build();
    }
}

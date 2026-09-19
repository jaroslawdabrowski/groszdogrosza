package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.CreateCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase.CollectionDetails;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.ListCollectionsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.RecordManualContributionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.RemoveStudentFromCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.SettleCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.AuthorizationSupport;
import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.ListStudentsUseCase;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 * {@code list}/{@code get} are available to any authenticated parent, but {@code get}
 * returns a different, smaller shape for a non-treasurer caller - see
 * {@link CollectionProgressResponse}: a regular parent gets aggregate progress only (how
 * many students' families paid, how much is still owed in total, a percentage), never the
 * per-student breakdown {@link CollectionDetailsResponse} carries, since that would expose
 * every other family's payment status and amounts. Creating, recording a manual contribution
 * and settling are real money-moving/administrative actions and are treasurer-only - see
 * {@code AuthorizationSupport}. Roles are checked explicitly (not {@code @RolesAllowed})
 * because the source of truth is {@code Parent.role()}, not an identity-provider claim.
 */
@Path("/api/collections")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class CollectionResource {

    @Inject
    CreateCollectionUseCase createCollectionUseCase;

    @Inject
    GetCollectionUseCase getCollectionUseCase;

    @Inject
    ListCollectionsUseCase listCollectionsUseCase;

    @Inject
    RecordManualContributionUseCase recordManualContributionUseCase;

    @Inject
    SettleCollectionUseCase settleCollectionUseCase;

    @Inject
    RemoveStudentFromCollectionUseCase removeStudentFromCollectionUseCase;

    @Inject
    ListStudentsUseCase listStudentsUseCase;

    @Inject
    AuthorizationSupport authorizationSupport;

    @Inject
    SecurityIdentity identity;

    @GET
    public List<CollectionResponse> list() {
        return listCollectionsUseCase.listCollections().stream().map(CollectionResponse::from).toList();
    }

    @POST
    public CollectionResponse create(CreateCollectionRequest request) {
        authorizationSupport.requireTreasurer(identity);
        return CollectionResponse.from(createCollectionUseCase.createCollection(
                request.title(), request.description(), request.baseAmountPerStudent(), request.studentIds()));
    }

    @GET
    @Path("/{id}")
    public Object get(@PathParam("id") String id) {
        CollectionDetails details = getCollectionUseCase.getCollection(id)
                .orElseThrow(() -> new NotFoundException("No such collection: " + id));
        return authorizationSupport.isTreasurer(identity)
                ? CollectionDetailsResponse.from(details, studentNamesById())
                : CollectionProgressResponse.from(details);
    }

    @POST
    @Path("/{id}/contributions")
    public CollectionDetailsResponse recordContribution(@PathParam("id") String id, RecordContributionRequest request) {
        authorizationSupport.requireTreasurer(identity);
        recordManualContributionUseCase.recordManualContribution(id, request.studentId(), request.amount());
        CollectionDetails details = getCollectionUseCase.getCollection(id)
                .orElseThrow(() -> new NotFoundException("No such collection: " + id));
        return CollectionDetailsResponse.from(details, studentNamesById());
    }

    @POST
    @Path("/{id}/settle")
    public SettlementResultResponse settle(@PathParam("id") String id, SettleCollectionRequest request) {
        authorizationSupport.requireTreasurer(identity);
        return SettlementResultResponse.from(settleCollectionUseCase.settleCollection(id, request.actualCostSpent()));
    }

    @DELETE
    @Path("/{id}/students/{studentId}")
    public CollectionDetailsResponse removeStudent(@PathParam("id") String id, @PathParam("studentId") String studentId) {
        authorizationSupport.requireTreasurer(identity);
        removeStudentFromCollectionUseCase.removeStudentFromCollection(id, studentId);
        CollectionDetails details = getCollectionUseCase.getCollection(id)
                .orElseThrow(() -> new NotFoundException("No such collection: " + id));
        return CollectionDetailsResponse.from(details, studentNamesById());
    }

    private Map<String, String> studentNamesById() {
        return listStudentsUseCase.listStudents().stream()
                .collect(java.util.stream.Collectors.toMap(Student::id, Student::fullName, (a, b) -> a));
    }
}

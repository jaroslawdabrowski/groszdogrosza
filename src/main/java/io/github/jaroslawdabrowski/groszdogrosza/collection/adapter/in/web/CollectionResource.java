package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.AddStudentToCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.CreateCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase.CollectionDetails;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.ListCollectionsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.RecordManualContributionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.RemoveStudentFromCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.SettleCollectionUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.domain.LedgerEventType;
import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.GetFullLedgerUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.AuthorizationSupport;
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
    AddStudentToCollectionUseCase addStudentToCollectionUseCase;

    @Inject
    ListStudentsUseCase listStudentsUseCase;

    @Inject
    GetFullLedgerUseCase getFullLedgerUseCase;

    @Inject
    AuthorizationSupport authorizationSupport;

    @Inject
    SecurityIdentity identity;

    /**
     * Every collection's summary, enriched with the caller's OWN child's status in it (see
     * {@link CollectionResponse#myStudentStatus}) - the one piece of per-student detail a
     * regular parent is allowed to see here, shown as a small indicator on the Dashboard's
     * cards. Costs one extra {@code getCollection} per collection (this app's usual "fine at
     * this scale" tradeoff, same as {@code PublicOverviewResource}), only when the caller
     * actually resolves to a Parent with a linked Student.
     */
    @GET
    public List<CollectionResponse> list() {
        String myStudentId = authorizationSupport.currentParent(identity).map(Parent::studentId).orElse(null);
        return listCollectionsUseCase.listCollections().stream()
                .map(collection -> CollectionResponse.from(collection, myStudentStatusFor(collection.id(), myStudentId)))
                .toList();
    }

    private String myStudentStatusFor(String collectionId, String myStudentId) {
        if (myStudentId == null) {
            return null;
        }
        return getCollectionUseCase.getCollection(collectionId)
                .flatMap(details -> details.requirements().stream()
                        .filter(r -> r.studentId().equals(myStudentId))
                        .findFirst())
                .map(r -> r.status().name())
                .orElse("NOT_INCLUDED");
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
        return collectionResponseFor(id);
    }

    @POST
    @Path("/{id}/contributions")
    public CollectionDetailsResponse recordContribution(@PathParam("id") String id, RecordContributionRequest request) {
        authorizationSupport.requireTreasurer(identity);
        recordManualContributionUseCase.recordManualContribution(id, request.studentId(), request.amount());
        CollectionDetails details = getCollectionUseCase.getCollection(id)
                .orElseThrow(() -> new NotFoundException("No such collection: " + id));
        String myStudentId = authorizationSupport.currentParent(identity).map(Parent::studentId).orElse(null);
        return CollectionDetailsResponse.from(details, listStudentsUseCase.listStudents(), myStudentId, removedStudentsCountFor(id));
    }

    @POST
    @Path("/{id}/settle")
    public SettlementResultResponse settle(@PathParam("id") String id, SettleCollectionRequest request) {
        authorizationSupport.requireTreasurer(identity);
        return SettlementResultResponse.from(settleCollectionUseCase.settleCollection(id, request.actualCostSpent()));
    }

    /**
     * Treasurer-only for any student; a regular parent may also call this, but only for
     * their OWN child ({@code requireSelfOrTreasurerForStudent} - a 403 if {@code studentId}
     * isn't the caller's own linked student) - a parent needing to pull their sick/departing
     * child out of an ACTIVE collection without going through the treasurer. Returns the
     * same role-dependent shape {@code get} does: the full per-student breakdown for a
     * treasurer, aggregate-only progress for a regular parent (never another family's
     * amounts). Only legal on an ACTIVE collection either way -
     * {@code RemoveStudentFromCollectionUseCase} enforces that regardless of caller.
     */
    @DELETE
    @Path("/{id}/students/{studentId}")
    public Object removeStudent(@PathParam("id") String id, @PathParam("studentId") String studentId) {
        authorizationSupport.requireSelfOrTreasurerForStudent(identity, studentId);
        removeStudentFromCollectionUseCase.removeStudentFromCollection(id, studentId);
        return collectionResponseFor(id);
    }

    /** Same self-or-treasurer rule as {@link #removeStudent} - a parent opting their own
     *  child back into an ACTIVE collection they'd been left off or removed from. */
    @POST
    @Path("/{id}/students/{studentId}")
    public Object addStudent(@PathParam("id") String id, @PathParam("studentId") String studentId) {
        authorizationSupport.requireSelfOrTreasurerForStudent(identity, studentId);
        addStudentToCollectionUseCase.addStudentToCollection(id, studentId);
        return collectionResponseFor(id);
    }

    private Object collectionResponseFor(String id) {
        CollectionDetails details = getCollectionUseCase.getCollection(id)
                .orElseThrow(() -> new NotFoundException("No such collection: " + id));
        // Computed regardless of role - the treasurer is also a Parent, possibly with their
        // OWN linked Student, and gets the same join/leave affordance on their own page a
        // regular parent does (see CollectionResponse#myStudentStatus's javadoc).
        String myStudentId = authorizationSupport.currentParent(identity).map(Parent::studentId).orElse(null);
        if (authorizationSupport.isTreasurer(identity)) {
            return CollectionDetailsResponse.from(
                    details, listStudentsUseCase.listStudents(), myStudentId, removedStudentsCountFor(id));
        }
        return CollectionProgressResponse.from(details, myStudentId);
    }

    /** See {@link CollectionDetailsResponse}'s own javadoc on {@code removedStudentsCount}
     *  for why this has to come from the ledger rather than {@code CollectionDetails} itself. */
    private int removedStudentsCountFor(String collectionId) {
        return (int) getFullLedgerUseCase.getFullLedger().stream()
                .filter(entry -> entry.eventType() == LedgerEventType.REMOVED_FROM_COLLECTION)
                .filter(entry -> collectionId.equals(entry.params().get("collectionId")))
                .count();
    }
}

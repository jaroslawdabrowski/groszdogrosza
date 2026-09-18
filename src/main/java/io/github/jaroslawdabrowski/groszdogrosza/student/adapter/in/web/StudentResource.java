package io.github.jaroslawdabrowski.groszdogrosza.student.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web.CreateParentRequest;
import io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web.ParentResponse;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.ParentRole;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreateParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsForStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.AuthorizationSupport;
import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.CreateStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.CreditStudentPiggyBankManuallyUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.DeleteStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.GetStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.ListStudentsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.UpdateStudentUseCase;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
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
import java.util.List;

/**
 * Every list/create/update/delete here is treasurer-only - a student's identity and their
 * parents' contact/login details are exactly the kind of cross-family data
 * {@code AuthorizationSupport} exists to keep behind the treasurer role. {@code get} is also
 * available to a parent for their own linked student (self-or-treasurer, see
 * {@code AuthorizationSupport.requireSelfOrTreasurerForStudent}) - that's how "Moja skarbonka"
 * works on the frontend.
 *
 * <p>Adding a parent happens here, under a specific student
 * ({@code POST /api/students/{id}/parents}), not as a standalone
 * {@code POST /api/parents} - see {@code Parent.studentId}'s javadoc for why a parent always
 * belongs to exactly one student.
 */
@Path("/api/students")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class StudentResource {

    @Inject
    CreateStudentUseCase createStudentUseCase;

    @Inject
    GetStudentUseCase getStudentUseCase;

    @Inject
    ListStudentsUseCase listStudentsUseCase;

    @Inject
    UpdateStudentUseCase updateStudentUseCase;

    @Inject
    DeleteStudentUseCase deleteStudentUseCase;

    @Inject
    CreditStudentPiggyBankManuallyUseCase creditStudentPiggyBankManuallyUseCase;

    @Inject
    CreateParentUseCase createParentUseCase;

    @Inject
    ListParentsForStudentUseCase listParentsForStudentUseCase;

    @Inject
    AuthorizationSupport authorizationSupport;

    @Inject
    SecurityIdentity identity;

    @GET
    public List<StudentResponse> list() {
        authorizationSupport.requireTreasurer(identity);
        return listStudentsUseCase.listStudents().stream()
                .map(student -> StudentResponse.from(student, listParentsForStudentUseCase.listParentsForStudent(student.id())))
                .toList();
    }

    @POST
    public StudentResponse create(CreateStudentRequest request) {
        authorizationSupport.requireTreasurer(identity);
        Student student = createStudentUseCase.createStudent(request.firstName(), request.lastName());
        return StudentResponse.from(student, List.of());
    }

    @GET
    @Path("/{id}")
    public StudentResponse get(@PathParam("id") String id) {
        Student student = requireStudent(id);
        authorizationSupport.requireSelfOrTreasurerForStudent(identity, id);
        return StudentResponse.from(student, listParentsForStudentUseCase.listParentsForStudent(id));
    }

    @PUT
    @Path("/{id}")
    public StudentResponse update(@PathParam("id") String id, UpdateStudentRequest request) {
        authorizationSupport.requireTreasurer(identity);
        Student updated = updateStudentUseCase.updateStudent(id, request.firstName(), request.lastName());
        return StudentResponse.from(updated, listParentsForStudentUseCase.listParentsForStudent(id));
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        authorizationSupport.requireTreasurer(identity);
        deleteStudentUseCase.deleteStudent(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/parents")
    public ParentResponse addParent(@PathParam("id") String id, CreateParentRequest request) {
        authorizationSupport.requireTreasurer(identity);
        requireStudent(id);
        ParentRole role = request.role() == null ? ParentRole.PARENT : ParentRole.valueOf(request.role());
        Parent parent = createParentUseCase.createParent(
                id, request.firstName(), request.lastName(), request.email(), request.expectedSenderName(), role);
        return ParentResponse.from(parent);
    }

    @POST
    @Path("/{id}/piggy-bank/credit")
    public StudentResponse creditPiggyBank(@PathParam("id") String id, CreditPiggyBankRequest request) {
        authorizationSupport.requireTreasurer(identity);
        Student updated = creditStudentPiggyBankManuallyUseCase.creditPiggyBankManually(id, request.amount());
        return StudentResponse.from(updated, listParentsForStudentUseCase.listParentsForStudent(id));
    }

    private Student requireStudent(String id) {
        return getStudentUseCase.getStudent(id).orElseThrow(() -> new NotFoundException("No such student: " + id));
    }
}

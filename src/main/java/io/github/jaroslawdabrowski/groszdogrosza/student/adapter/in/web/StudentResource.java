package io.github.jaroslawdabrowski.groszdogrosza.student.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web.CreateParentRequest;
import io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web.ParentResponse;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.ParentRole;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.CreateParentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsForStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.ClassContext;
import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.CreateStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.CreditStudentPiggyBankManuallyUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.DeleteStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.GetStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.ListStudentsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.UpdateStudentUseCase;
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
import java.util.List;

/**
 * A class's students. Membership of the class is checked before any of this runs
 * (platform.security.ClassAccessFilter); here it's only about the role: the roster and every
 * change are treasurer-only, a single student is visible to the treasurer and that student's
 * own parents.
 */
@Path("/api/classes/{classId}/students")
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
    ClassContext classContext;

    @GET
    public List<StudentResponse> list() {
        classContext.requireTreasurer();
        String classId = classContext.classId();
        return listStudentsUseCase.listStudents(classId).stream()
                .map(student -> StudentResponse.from(student, listParentsForStudentUseCase.listParentsForStudent(classId, student.id())))
                .toList();
    }

    @POST
    public StudentResponse create(CreateStudentRequest request) {
        classContext.requireTreasurer();
        Student student = createStudentUseCase.createStudent(classContext.classId(), request.firstName(), request.lastName());
        return StudentResponse.from(student, List.of());
    }

    @GET
    @Path("/{id}")
    public StudentResponse get(@PathParam("id") String id) {
        classContext.requireTreasurerOrGuardianOf(id);
        Student student = requireStudent(id);
        return StudentResponse.from(student, listParentsForStudentUseCase.listParentsForStudent(classContext.classId(), id));
    }

    @PUT
    @Path("/{id}")
    public StudentResponse update(@PathParam("id") String id, UpdateStudentRequest request) {
        classContext.requireTreasurer();
        String classId = classContext.classId();
        Student updated = updateStudentUseCase.updateStudent(classId, id, request.firstName(), request.lastName());
        return StudentResponse.from(updated, listParentsForStudentUseCase.listParentsForStudent(classId, id));
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        classContext.requireTreasurer();
        deleteStudentUseCase.deleteStudent(classContext.classId(), id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/parents")
    public ParentResponse addParent(@PathParam("id") String id, CreateParentRequest request) {
        classContext.requireTreasurer();
        requireStudent(id);
        Parent parent = createParentUseCase.createParent(classContext.classId(), id, request.firstName(),
                request.lastName(), request.email(), request.expectedSenderName(), ParentRole.PARENT);
        return ParentResponse.from(parent);
    }

    @POST
    @Path("/{id}/piggy-bank/credit")
    public StudentResponse creditPiggyBank(@PathParam("id") String id, CreditPiggyBankRequest request) {
        classContext.requireTreasurer();
        String classId = classContext.classId();
        Student updated = creditStudentPiggyBankManuallyUseCase.creditPiggyBankManually(classId, id, request.amount());
        return StudentResponse.from(updated, listParentsForStudentUseCase.listParentsForStudent(classId, id));
    }

    private Student requireStudent(String id) {
        return getStudentUseCase.getStudent(classContext.classId(), id)
                .orElseThrow(() -> new NotFoundException("No such student: " + id));
    }
}

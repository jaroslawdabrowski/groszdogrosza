package io.github.jaroslawdabrowski.groszdogrosza.ledger.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.GetLedgerForStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.ClassContext;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.GetStudentUseCase;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * A student's ledger is exactly as sensitive as their piggy bank balance itself - see
 * {@code StudentResource.get} and {@code AuthorizationSupport.requireSelfOrTreasurerForStudent}
 * for the same self-or-treasurer rule applied here ("self" = the caller's own Parent record
 * is linked to this student).
 */
@Path("/api/classes/{classId}/students/{studentId}/ledger")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class LedgerResource {

    @Inject
    GetLedgerForStudentUseCase getLedgerForStudentUseCase;

    @Inject
    GetStudentUseCase getStudentUseCase;

    @Inject
    ClassContext classContext;

    @GET
    public List<LedgerEntryResponse> get(@PathParam("studentId") String studentId) {
        classContext.requireTreasurerOrGuardianOf(studentId);
        getStudentUseCase.getStudent(classContext.classId(), studentId)
                .orElseThrow(() -> new NotFoundException("No such student: " + studentId));
        return getLedgerForStudentUseCase.getLedgerFor(classContext.classId(), studentId).stream().map(LedgerEntryResponse::from).toList();
    }
}

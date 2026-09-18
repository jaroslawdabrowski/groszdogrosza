package io.github.jaroslawdabrowski.groszdogrosza.ledger.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.GetLedgerForStudentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.AuthorizationSupport;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.GetStudentUseCase;
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
 * A student's ledger is exactly as sensitive as their piggy bank balance itself - see
 * {@code StudentResource.get} and {@code AuthorizationSupport.requireSelfOrTreasurerForStudent}
 * for the same self-or-treasurer rule applied here ("self" = the caller's own Parent record
 * is linked to this student).
 */
@Path("/api/students/{studentId}/ledger")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class LedgerResource {

    @Inject
    GetLedgerForStudentUseCase getLedgerForStudentUseCase;

    @Inject
    GetStudentUseCase getStudentUseCase;

    @Inject
    AuthorizationSupport authorizationSupport;

    @Inject
    SecurityIdentity identity;

    @GET
    public List<LedgerEntryResponse> get(@PathParam("studentId") String studentId) {
        getStudentUseCase.getStudent(studentId).orElseThrow(() -> new NotFoundException("No such student: " + studentId));
        authorizationSupport.requireSelfOrTreasurerForStudent(identity, studentId);
        return getLedgerForStudentUseCase.getLedgerFor(studentId).stream().map(LedgerEntryResponse::from).toList();
    }
}

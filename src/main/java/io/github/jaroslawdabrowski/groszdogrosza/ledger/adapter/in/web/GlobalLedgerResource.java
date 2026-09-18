package io.github.jaroslawdabrowski.groszdogrosza.ledger.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.ledger.port.in.GetFullLedgerUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.AuthorizationSupport;
import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import io.github.jaroslawdabrowski.groszdogrosza.student.port.in.ListStudentsUseCase;
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
 * Treasurer-only "log wszystkich transakcji" - every student's ledger entries in one feed,
 * unlike {@code LedgerResource} which only ever returns one student's own (self-or-treasurer).
 */
@Path("/api/ledger")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class GlobalLedgerResource {

    @Inject
    GetFullLedgerUseCase getFullLedgerUseCase;

    @Inject
    ListStudentsUseCase listStudentsUseCase;

    @Inject
    AuthorizationSupport authorizationSupport;

    @Inject
    SecurityIdentity identity;

    @GET
    public List<GlobalLedgerEntryResponse> get() {
        authorizationSupport.requireTreasurer(identity);
        Map<String, String> namesByStudentId = listStudentsUseCase.listStudents().stream()
                .collect(java.util.stream.Collectors.toMap(Student::id, Student::fullName, (a, b) -> a));
        return getFullLedgerUseCase.getFullLedger().stream()
                .map(entry -> GlobalLedgerEntryResponse.from(entry,
                        namesByStudentId.getOrDefault(entry.studentId(), entry.studentId())))
                .toList();
    }
}

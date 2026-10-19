package io.github.jaroslawdabrowski.groszdogrosza.platform.security;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsByEmailUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.SchoolClass;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in.GetSchoolClassUseCase;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Who the logged-in user is and which classes they belong to - the first thing the frontend
 * asks after login, to know which class to show and in what role. Only the caller's own
 * classes are listed; nothing about other classes is revealed.
 */
@Path("/api/me")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class MeResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    ClassMembershipResolver membershipResolver;

    @Inject
    GetSchoolClassUseCase getSchoolClassUseCase;

    @Inject
    ListParentsByEmailUseCase listParentsByEmailUseCase;

    /** The app's administrators (manage classes and treasurers) - server configuration only,
     *  never stored in the data, so it can't be granted from inside the app. */
    @ConfigProperty(name = "groszdogrosza.admin-emails")
    Optional<List<String>> adminEmails;

    @GET
    public MeResponse me() {
        String email = CallerEmail.of(identity);
        List<MembershipResponse> classes = membershipResolver.memberships(email).stream()
                .flatMap(membership -> getSchoolClassUseCase.getSchoolClass(membership.classId())
                        .map(schoolClass -> MembershipResponse.from(schoolClass, membership)).stream())
                .sorted(Comparator.comparing(MembershipResponse::name))
                .toList();
        String firstName = listParentsByEmailUseCase.listParentsByEmail(email).stream()
                .map(Parent::firstName)
                .findFirst()
                .orElse(null);
        return new MeResponse(email, firstName, isAdmin(email), classes);
    }

    private boolean isAdmin(String email) {
        return email != null && adminEmails.orElse(List.of()).stream()
                .anyMatch(admin -> admin.trim().toLowerCase(Locale.ROOT).equals(email));
    }

    public record MeResponse(String email, String firstName, boolean admin, List<MembershipResponse> classes) {
    }

    public record MembershipResponse(String id, String name, String role, Set<String> studentIds, Set<String> parentIds) {

        static MembershipResponse from(SchoolClass schoolClass, ClassMembership membership) {
            return new MembershipResponse(schoolClass.id(), schoolClass.name(), membership.role().name(),
                    membership.studentIds(), membership.parentIds());
        }
    }
}

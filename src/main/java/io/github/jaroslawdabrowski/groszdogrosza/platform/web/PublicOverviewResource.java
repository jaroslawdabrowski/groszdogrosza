package io.github.jaroslawdabrowski.groszdogrosza.platform.web;

import io.github.jaroslawdabrowski.groszdogrosza.platform.security.CallerEmail;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.ClassMembership;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.ClassMembershipResolver;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.SchoolClass;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in.FindSchoolClassByPublicTokenUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in.GetSchoolClassUseCase;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The deliberately unauthenticated pages (no {@code @Authenticated}; carved out of /api/* in
 * application.properties): a class's totals and payment details for anyone with its link.
 *
 * <p>The answer never depends on who's asking beyond "is this a parent IN THIS class": a
 * logged-in member of another class holding the link gets exactly what an anonymous visitor
 * gets (docs/MULTI-CLASS.md, "Public page").
 */
@Path("/api/public")
@Produces(MediaType.APPLICATION_JSON)
public class PublicOverviewResource {

    @Inject
    FindSchoolClassByPublicTokenUseCase findSchoolClassByPublicTokenUseCase;

    @Inject
    GetSchoolClassUseCase getSchoolClassUseCase;

    @Inject
    ClassOverviewAssembler classOverviewAssembler;

    @Inject
    ClassMembershipResolver membershipResolver;

    @Inject
    SecurityIdentity identity;

    /** The app's address without a class link ("/") keeps showing the original class, so the
     *  links parents already have keep working. Empty = no such page. */
    @ConfigProperty(name = "groszdogrosza.public.default-class-id")
    Optional<String> defaultClassId;

    @GET
    @Path("/overview")
    public PublicOverviewResponse defaultOverview() {
        SchoolClass schoolClass = defaultClassId.filter(id -> !id.isBlank())
                .flatMap(getSchoolClassUseCase::getSchoolClass)
                .orElseThrow(() -> new NotFoundException("No default class"));
        return overviewFor(schoolClass);
    }

    @GET
    @Path("/classes/{token}/overview")
    public PublicOverviewResponse overview(@PathParam("token") String token) {
        SchoolClass schoolClass = findSchoolClassByPublicTokenUseCase.findByPublicToken(token)
                .orElseThrow(() -> new NotFoundException("No such class"));
        return overviewFor(schoolClass);
    }

    private PublicOverviewResponse overviewFor(SchoolClass schoolClass) {
        String myStudentId = membershipResolver.membership(schoolClass.id(), CallerEmail.of(identity))
                .flatMap(membership -> membership.studentIds().stream().sorted().findFirst())
                .orElse(null);
        return classOverviewAssembler.build(schoolClass, myStudentId);
    }
}

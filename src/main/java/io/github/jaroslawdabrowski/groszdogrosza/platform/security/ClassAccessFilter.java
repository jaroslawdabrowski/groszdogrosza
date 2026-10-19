package io.github.jaroslawdabrowski.groszdogrosza.platform.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * The gatekeeper in front of every class endpoint ({@code /api/classes/{classId}/...}): before
 * any resource code runs, it checks that the logged-in user is a member of that class
 * (treasurer, or guardian of one of its students) and fills in {@link ClassContext}. A
 * non-member gets 404 - the same answer as for a class that doesn't exist, so the response
 * doesn't even confirm the class is there. See docs/MULTI-CLASS.md, "Isolation between
 * classes".
 *
 * <p>It matches on the request path, not on an annotation, so a new class endpoint can't be
 * left out by forgetting to annotate it; {@code ClassEndpointsGuardTest} additionally fails
 * the build if an endpoint under {@code /api} is neither class-scoped nor on the short list
 * of deliberately global ones.
 */
public class ClassAccessFilter {

    @Inject
    SecurityIdentity identity;

    @Inject
    ClassMembershipResolver membershipResolver;

    @Inject
    ClassContext classContext;

    @ServerRequestFilter
    public Optional<Response> requireClassMembership(ContainerRequestContext request) {
        String classId = ClassAccessFilterPaths.classIdOf(request.getUriInfo().getPath());
        if (classId == null) {
            return Optional.empty();
        }
        Optional<ClassMembership> membership = membershipResolver.membership(classId, CallerEmail.of(identity));
        if (membership.isEmpty()) {
            return Optional.of(Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "not-found"))
                    .build());
        }
        classContext.set(membership.get());
        return Optional.empty();
    }
}

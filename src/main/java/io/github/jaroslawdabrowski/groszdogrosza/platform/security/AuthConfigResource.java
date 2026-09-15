package io.github.jaroslawdabrowski.groszdogrosza.platform.security;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Lets the frontend discover which OIDC provider to talk to (local Keycloak Dev Services
 * vs. AWS Cognito) without baking either into the Angular build - the same built bundle
 * works against both. Deliberately public (see quarkus.http.auth.permission.public in
 * application.properties): the frontend needs this before it has a token.
 */
@Path("/api/auth-config")
public class AuthConfigResource {

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String issuer;

    @ConfigProperty(name = "quarkus.oidc.client-id")
    String clientId;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public AuthConfigResponse get() {
        return new AuthConfigResponse(issuer, clientId);
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.parent.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import java.util.Optional;

/**
 * Resolves the currently authenticated caller's own Parent record by matching their OIDC
 * token's {@code email} claim - see {@code platform.security.AuthorizationSupport}, the only
 * caller of this use case.
 */
public interface GetParentByEmailUseCase {

    Optional<Parent> getParentByEmail(String email);
}

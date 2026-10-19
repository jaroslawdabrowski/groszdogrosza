package io.github.jaroslawdabrowski.groszdogrosza.parent.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import java.util.List;

/**
 * Resolves the currently authenticated caller's own Parent record by matching their OIDC
 * token's {@code email} claim - see {@code platform.security.AuthorizationSupport}, the only
 * caller of this use case.
 */
public interface ListParentsByEmailUseCase {

    /** Every parent record with this email, in any class - one login can be a parent of
     *  children in several classes. Only for working out which classes the caller belongs to. */
    List<Parent> listParentsByEmail(String email);
}

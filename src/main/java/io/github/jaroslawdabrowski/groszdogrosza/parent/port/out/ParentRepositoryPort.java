package io.github.jaroslawdabrowski.groszdogrosza.parent.port.out;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import java.util.List;
import java.util.Optional;

public interface ParentRepositoryPort {

    Parent save(Parent parent);

    Optional<Parent> findById(String parentId);

    /** Case-insensitive - used to resolve the current OIDC caller's own Parent record by
     * their token's {@code email} claim (see {@code platform.security.AuthorizationSupport}). */
    Optional<Parent> findByEmail(String email);

    List<Parent> findAll();
}

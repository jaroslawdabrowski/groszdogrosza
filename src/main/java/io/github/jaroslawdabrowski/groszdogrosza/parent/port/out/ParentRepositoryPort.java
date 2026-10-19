package io.github.jaroslawdabrowski.groszdogrosza.parent.port.out;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import java.util.List;
import java.util.Optional;

public interface ParentRepositoryPort {

    Parent save(Parent parent);

    /** Empty when the parent doesn't exist OR belongs to another class. */
    Optional<Parent> findById(String classId, String parentId);

    /** Case-insensitive - used to resolve the current OIDC caller's own Parent record by
     * their token's {@code email} claim (see {@code platform.security.AuthorizationSupport}). */
    /** Every parent record with this email, across all classes (case-insensitive). */
    List<Parent> findByEmail(String email);

    List<Parent> findAllInClass(String classId);

    /** At most 2 in practice (see {@code Parent}'s javadoc) - never enforced by this port
     *  itself, callers (e.g. {@code ParentService.createParent}) check the count. */
    List<Parent> findByStudentId(String classId, String studentId);

    /** No-op if the id doesn't exist. */
    void deleteById(String classId, String parentId);
}

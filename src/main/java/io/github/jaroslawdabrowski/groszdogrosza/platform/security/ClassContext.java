package io.github.jaroslawdabrowski.groszdogrosza.platform.security;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

/**
 * The class a request is about and what the caller may do in it - filled in by
 * {@link ClassAccessFilter} before any class endpoint runs, so resource code never decides
 * membership itself and never reads the class id from a request body.
 *
 * <p>Calling {@link #classId()} on a request the filter didn't vet (anything outside
 * {@code /api/classes/{classId}/...}) fails loudly instead of returning null.
 */
@RequestScoped
public class ClassContext {

    private ClassMembership membership;

    void set(ClassMembership membership) {
        this.membership = membership;
    }

    public ClassMembership membership() {
        if (membership == null) {
            throw new IllegalStateException("No class context - this endpoint isn't under /api/classes/{classId}");
        }
        return membership;
    }

    public String classId() {
        return membership().classId();
    }

    public boolean isTreasurer() {
        return membership().isTreasurer();
    }

    /** The caller's own children in this class (empty for a treasurer without a child here). */
    public java.util.Set<String> ownStudentIds() {
        return membership().studentIds();
    }

    /** The first of the caller's own children in this class, or null. */
    public String firstOwnStudentId() {
        return ownStudentIds().stream().sorted().findFirst().orElse(null);
    }

    /** Treasurer-only actions. The caller is a member here (the filter checked), so 403 is
     *  fine - it reveals nothing they don't already know. */
    public void requireTreasurer() {
        if (!isTreasurer()) {
            throw new ForbiddenException("This action is restricted to the class's treasurer");
        }
    }

    /** A child's details: the treasurer, or that child's own guardian. Anyone else gets 404,
     *  as if the child didn't exist. */
    public void requireTreasurerOrGuardianOf(String studentId) {
        if (!isTreasurer() && !ownStudentIds().contains(studentId)) {
            throw new NotFoundException("No such student: " + studentId);
        }
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.platform.security;

import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.ClassRole;
import java.util.Set;

/**
 * What the logged-in user is in one class.
 *
 * @param studentIds the students of this class the user is a guardian of (may be empty for a
 *                   treasurer with no child in the class)
 * @param parentIds  the user's own parent records in this class
 */
public record ClassMembership(String classId, ClassRole role, Set<String> studentIds, Set<String> parentIds) {

    public boolean isTreasurer() {
        return role == ClassRole.TREASURER;
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.platform.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.ClassRole;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClassContextTest {

    private static ClassContext context(ClassRole role, Set<String> ownStudents) {
        ClassContext context = new ClassContext();
        context.set(new ClassMembership("class-a", role, ownStudents, Set.of()));
        return context;
    }

    @Test
    void aTreasurerSeesEveryStudentOfTheClass() {
        ClassContext treasurer = context(ClassRole.TREASURER, Set.of());
        assertDoesNotThrow(treasurer::requireTreasurer);
        assertDoesNotThrow(() -> treasurer.requireTreasurerOrGuardianOf("any-student"));
    }

    @Test
    void aParentSeesOnlyTheirOwnChildAndNothingTreasurerOnly() {
        ClassContext parent = context(ClassRole.PARENT, Set.of("my-child"));
        assertDoesNotThrow(() -> parent.requireTreasurerOrGuardianOf("my-child"));
        // Someone else's child is "not found", not "forbidden" - it doesn't confirm the id exists.
        assertThrows(NotFoundException.class, () -> parent.requireTreasurerOrGuardianOf("other-child"));
        assertThrows(ForbiddenException.class, parent::requireTreasurer);
    }

    @Test
    void usingTheContextOutsideAClassRequestFailsLoudly() {
        ClassContext unset = new ClassContext();
        assertThrows(IllegalStateException.class, unset::classId);
    }
}

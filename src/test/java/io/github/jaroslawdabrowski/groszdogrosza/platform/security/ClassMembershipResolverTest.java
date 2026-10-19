package io.github.jaroslawdabrowski.groszdogrosza.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.ParentRole;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.ClassRole;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClassMembershipResolverTest {

    private static final String ANNA = "anna@example.com";

    private static Parent parent(String id, String classId, String studentId, String email) {
        return new Parent(id, classId, studentId, "Anna", "Kowalska", email, "Anna Kowalska", null, ParentRole.PARENT, null);
    }

    /** Anna: parent of Kasia in 2A and of Jaś in 4B, treasurer of 1C (no child there). */
    private static ClassMembershipResolver resolver() {
        ClassMembershipResolver resolver = new ClassMembershipResolver();
        List<Parent> annasParents = List.of(parent("p1", "2a", "kasia", ANNA), parent("p2", "4b", "jas", ANNA));
        resolver.listParentsByEmailUseCase = email -> ANNA.equals(email) ? annasParents : List.of();
        resolver.isClassTreasurerUseCase = (classId, email) -> "1c".equals(classId) && ANNA.equals(email);
        resolver.listTreasurerClassesUseCase = email -> ANNA.equals(email) ? List.of("1c") : List.of();
        return resolver;
    }

    @Test
    void aParentIsAMemberOnlyOfTheirChildrensClassesSeeingOnlyThatClassesChild() {
        var membership = resolver().membership("2a", ANNA).orElseThrow();
        assertEquals(ClassRole.PARENT, membership.role());
        assertEquals(Set.of("kasia"), membership.studentIds());
    }

    @Test
    void treasurerOfOneClassIsNothingInAClassWhereSheHasNoRecord() {
        assertEquals(ClassRole.TREASURER, resolver().membership("1c", ANNA).orElseThrow().role());
        assertTrue(resolver().membership("9z", ANNA).isEmpty());
    }

    @Test
    void someoneElseOrNobodyIsNeverAMember() {
        assertTrue(resolver().membership("2a", "piotr@example.com").isEmpty());
        assertTrue(resolver().membership("2a", null).isEmpty());
    }

    @Test
    void listsEveryClassOnceTreasurerFirst() {
        var classes = resolver().memberships(ANNA).stream().map(ClassMembership::classId).toList();
        assertEquals(List.of("1c", "2a", "4b"), classes);
    }
}

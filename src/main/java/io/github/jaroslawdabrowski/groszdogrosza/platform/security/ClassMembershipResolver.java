package io.github.jaroslawdabrowski.groszdogrosza.platform.security;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.ListParentsByEmailUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.ClassRole;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in.IsClassTreasurerUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in.ListTreasurerClassesUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Works out which classes a (verified) email belongs to and in what role - the one place that
 * decides membership. A user is:
 * <ul>
 *   <li>a TREASURER of a class when there's a treasurer record for their email in it,</li>
 *   <li>otherwise a PARENT of a class when one of their parent records (same email) is
 *       attached to a student of that class,</li>
 *   <li>otherwise not a member at all - and then the class doesn't exist for them.</li>
 * </ul>
 * Being the app's administrator grants nothing here: the admin sees a class's data only by
 * also being a member (docs/MULTI-CLASS.md, "What the admin sees").
 */
@ApplicationScoped
public class ClassMembershipResolver {

    @Inject
    IsClassTreasurerUseCase isClassTreasurerUseCase;

    @Inject
    ListTreasurerClassesUseCase listTreasurerClassesUseCase;

    @Inject
    ListParentsByEmailUseCase listParentsByEmailUseCase;

    public Optional<ClassMembership> membership(String classId, String email) {
        if (classId == null || email == null) {
            return Optional.empty();
        }
        List<Parent> ownParentsInClass = listParentsByEmailUseCase.listParentsByEmail(email).stream()
                .filter(parent -> classId.equals(parent.classId()))
                .toList();
        boolean treasurer = isClassTreasurerUseCase.isTreasurer(classId, email);
        if (!treasurer && ownParentsInClass.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ClassMembership(classId, treasurer ? ClassRole.TREASURER : ClassRole.PARENT,
                studentIds(ownParentsInClass), parentIds(ownParentsInClass)));
    }

    /** Every class the email belongs to, treasurer roles first, each class once. */
    public List<ClassMembership> memberships(String email) {
        if (email == null) {
            return List.of();
        }
        Map<String, List<Parent>> parentsByClass = listParentsByEmailUseCase.listParentsByEmail(email).stream()
                .collect(Collectors.groupingBy(Parent::classId, LinkedHashMap::new, Collectors.toList()));
        Map<String, ClassMembership> result = new LinkedHashMap<>();
        for (String classId : listTreasurerClassesUseCase.classIdsWhereTreasurer(email)) {
            List<Parent> own = parentsByClass.getOrDefault(classId, List.of());
            result.put(classId, new ClassMembership(classId, ClassRole.TREASURER, studentIds(own), parentIds(own)));
        }
        parentsByClass.forEach((classId, own) -> result.putIfAbsent(classId,
                new ClassMembership(classId, ClassRole.PARENT, studentIds(own), parentIds(own))));
        return List.copyOf(result.values());
    }

    private static Set<String> studentIds(List<Parent> parents) {
        return parents.stream().map(Parent::studentId).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> parentIds(List<Parent> parents) {
        return parents.stream().map(Parent::id).collect(Collectors.toUnmodifiableSet());
    }
}

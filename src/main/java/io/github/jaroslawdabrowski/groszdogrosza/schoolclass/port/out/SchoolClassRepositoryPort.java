package io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.out;

import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.SchoolClass;
import java.util.List;
import java.util.Optional;

public interface SchoolClassRepositoryPort {

    Optional<SchoolClass> findById(String classId);

    Optional<SchoolClass> findByPublicToken(String publicToken);

    SchoolClass save(SchoolClass schoolClass);

    /** Whether this email is a treasurer of this class (case-insensitive). */
    boolean isTreasurer(String classId, String email);

    /** Ids of every class this email is a treasurer of. */
    List<String> findClassIdsWhereTreasurer(String email);
}

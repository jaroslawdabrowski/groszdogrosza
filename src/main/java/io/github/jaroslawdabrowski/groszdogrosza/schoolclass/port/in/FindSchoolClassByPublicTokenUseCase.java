package io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in;

import java.util.Optional;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.SchoolClass;

public interface FindSchoolClassByPublicTokenUseCase {

    Optional<SchoolClass> findByPublicToken(String publicToken);
}

package io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in;

import java.util.Optional;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.SchoolClass;

public interface GetSchoolClassUseCase {

    Optional<SchoolClass> getSchoolClass(String classId);
}

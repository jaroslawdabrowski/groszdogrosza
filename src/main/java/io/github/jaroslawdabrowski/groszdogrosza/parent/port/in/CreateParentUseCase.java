package io.github.jaroslawdabrowski.groszdogrosza.parent.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.ParentRole;

/** @throws io.github.jaroslawdabrowski.groszdogrosza.parent.domain.TooManyParentsException
 *          if the student already has 2 parents */
public interface CreateParentUseCase {

    Parent createParent(String studentId, String firstName, String lastName, String email, String expectedSenderName,
            ParentRole role);
}

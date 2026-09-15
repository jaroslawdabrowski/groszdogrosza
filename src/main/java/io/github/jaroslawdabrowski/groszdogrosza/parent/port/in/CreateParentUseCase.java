package io.github.jaroslawdabrowski.groszdogrosza.parent.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.ParentRole;

public interface CreateParentUseCase {

    Parent createParent(String firstName, String lastName, String email, String expectedSenderName, ParentRole role);
}

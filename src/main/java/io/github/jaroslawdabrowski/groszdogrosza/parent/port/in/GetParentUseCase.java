package io.github.jaroslawdabrowski.groszdogrosza.parent.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import java.util.Optional;

public interface GetParentUseCase {

    Optional<Parent> getParent(String parentId);
}

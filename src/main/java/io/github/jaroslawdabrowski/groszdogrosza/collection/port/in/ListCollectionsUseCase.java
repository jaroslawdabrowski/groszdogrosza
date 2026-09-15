package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import java.util.List;

public interface ListCollectionsUseCase {

    List<Collection> listCollections();
}

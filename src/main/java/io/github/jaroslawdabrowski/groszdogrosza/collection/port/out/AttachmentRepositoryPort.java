package io.github.jaroslawdabrowski.groszdogrosza.collection.port.out;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.CollectionAttachment;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepositoryPort {

    CollectionAttachment saveAttachment(CollectionAttachment attachment);

    List<CollectionAttachment> findAttachmentsByCollectionId(String collectionId);

    Optional<CollectionAttachment> findAttachment(String collectionId, String attachmentId);

    void deleteAttachment(String collectionId, String attachmentId);
}

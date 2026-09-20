package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.CollectionAttachment;
import java.util.List;

/**
 * Available to anyone with access to the collection (any authenticated parent), not just
 * the treasurer - see CollectionAttachmentResource's javadoc. Each returned attachment's
 * {@code viewUrl} is a short-lived presigned GET URL, freshly minted per call rather than
 * stored, since a stored URL would eventually expire while still "on the page".
 */
public interface ListAttachmentsUseCase {

    List<AttachmentWithViewUrl> listAttachments(String collectionId);

    record AttachmentWithViewUrl(CollectionAttachment attachment, String viewUrl) {
    }
}

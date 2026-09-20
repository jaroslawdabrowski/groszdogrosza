package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

/** Treasurer-only (enforced by the resource, see AuthorizationSupport) - removes both the S3 object and its metadata row. */
public interface DeleteAttachmentUseCase {

    void deleteAttachment(String collectionId, String attachmentId);
}

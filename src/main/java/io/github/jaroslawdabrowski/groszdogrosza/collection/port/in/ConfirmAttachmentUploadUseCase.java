package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.CollectionAttachment;

/**
 * Second step of the upload flow - called by the browser right after a successful PUT to
 * the presigned URL from {@link RequestAttachmentUploadUseCase}. Verifies the object
 * actually landed in S3 (a client that never finished the PUT, or that abandoned the
 * upload, must not produce a metadata row for a file that doesn't exist) and only then
 * writes the {@link CollectionAttachment} row.
 */
public interface ConfirmAttachmentUploadUseCase {

    CollectionAttachment confirmUpload(String collectionId, String attachmentId, String fileName, String contentType,
            long sizeBytes);
}

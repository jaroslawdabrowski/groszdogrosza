package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

/**
 * First step of a direct-to-S3 upload: mints an attachment id + S3 key and a short-lived
 * presigned PUT URL the browser uses to upload the file's bytes directly, bypassing the
 * Lambda entirely (its Function URL has a synchronous payload limit far below the 10MB an
 * attachment can be). No metadata is persisted yet - see
 * {@link ConfirmAttachmentUploadUseCase} for the step that actually records the attachment,
 * so an upload the browser never finishes never becomes a phantom entry the treasurer can
 * "see" but not open.
 */
public interface RequestAttachmentUploadUseCase {

    UploadTicket requestUpload(String collectionId, String fileName, String contentType, long sizeBytes);

    record UploadTicket(String attachmentId, String uploadUrl) {
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

import java.util.Set;

/**
 * What's allowed as a collection attachment - the user's own explicit choice (asked via
 * AskUserQuestion): images (jpg/png/heic, the formats a phone camera actually produces) plus
 * PDF (a scanned/exported receipt), capped at 10MB. Enforced when an upload is requested
 * ({@code RequestAttachmentUploadUseCase}) - the presigned PUT URL itself doesn't restrict
 * content type or size, so this is a pure application-level check, not a hard guarantee
 * against a malicious client bypassing the app's own API; acceptable at this app's
 * single-treasurer scale (see CLAUDE.md's "simplest thing that's still correct" theme).
 */
public final class AttachmentPolicy {

    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/heic", "application/pdf");

    private AttachmentPolicy() {
    }

    public static boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    public static void validate(String contentType, long sizeBytes) {
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new UnsupportedAttachmentException("Unsupported file type: " + contentType);
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
            throw new UnsupportedAttachmentException("File size must be between 1 byte and 10MB");
        }
    }
}

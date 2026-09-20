package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

import java.time.Instant;

/**
 * A photo or document (e.g. a receipt or invoice) the treasurer attaches to a collection to
 * document what the collected money was actually spent on - see CLAUDE.md, "Collection
 * attachments". {@code s3Key} is the object key in the attachments S3 bucket; the file's
 * bytes never pass through the Lambda (see {@code AttachmentStoragePort}), so this record is
 * pure metadata.
 */
public record CollectionAttachment(
        String id, String collectionId, String fileName, String contentType, long sizeBytes, String s3Key,
        Instant uploadedAt) {
}

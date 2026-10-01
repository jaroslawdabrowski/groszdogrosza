package io.github.jaroslawdabrowski.groszdogrosza.collection.port.out;

import java.util.OptionalLong;

/** S3-backed object storage for attachment bytes - see {@code S3AttachmentStorageAdapter}. */
public interface AttachmentStoragePort {

    String presignPutUrl(String s3Key, String contentType);

    String presignGetUrl(String s3Key, String fileName);

    /** The stored object's size in bytes, or empty if nothing was uploaded under this key. */
    OptionalLong objectSize(String s3Key);

    void deleteObject(String s3Key);
}

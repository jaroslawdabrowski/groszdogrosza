package io.github.jaroslawdabrowski.groszdogrosza.collection.port.out;

/** S3-backed object storage for attachment bytes - see {@code S3AttachmentStorageAdapter}. */
public interface AttachmentStoragePort {

    String presignPutUrl(String s3Key, String contentType);

    String presignGetUrl(String s3Key, String fileName);

    boolean objectExists(String s3Key);

    void deleteObject(String s3Key);
}

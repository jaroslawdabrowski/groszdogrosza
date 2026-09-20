package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.out.s3;

import io.github.jaroslawdabrowski.groszdogrosza.collection.port.out.AttachmentStoragePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Direct-to-S3 upload/download via presigned URLs - the file's bytes never pass through the
 * Lambda in either direction, see the two use-case ports' javadoc for why (the Function
 * URL's synchronous payload limit). Presigned URLs are short-lived (10 minutes is plenty for
 * a single PUT/GET the browser issues immediately) rather than durable links - a document
 * viewed "later" always gets a freshly minted URL from {@code ListAttachmentsUseCase}.
 */
@ApplicationScoped
public class S3AttachmentStorageAdapter implements AttachmentStoragePort {

    private static final Duration PRESIGN_DURATION = Duration.ofMinutes(10);

    @Inject
    S3Client s3Client;

    @Inject
    S3Presigner s3Presigner;

    @ConfigProperty(name = "groszdogrosza.attachments.bucket-name")
    String bucketName;

    @Override
    public String presignPutUrl(String s3Key, String contentType) {
        PutObjectRequest putObjectRequest =
                PutObjectRequest.builder().bucket(bucketName).key(s3Key).contentType(contentType).build();
        return s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(PRESIGN_DURATION)
                        .putObjectRequest(putObjectRequest)
                        .build())
                .url().toString();
    }

    @Override
    public String presignGetUrl(String s3Key, String fileName) {
        // Content-Disposition so a browser tab that opens the URL directly (e.g. from the
        // frontend's <a href> link) shows/downloads it under the original filename rather
        // than the opaque UUID s3Key.
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .responseContentDisposition("inline; filename=\"" + fileName.replace("\"", "") + "\"")
                .build();
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(PRESIGN_DURATION)
                        .getObjectRequest(getObjectRequest)
                        .build())
                .url().toString();
    }

    @Override
    public boolean objectExists(String s3Key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(s3Key).build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void deleteObject(String s3Key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(s3Key).build());
    }
}

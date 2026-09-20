package io.github.jaroslawdabrowski.groszdogrosza.collection.application;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.AttachmentPolicy;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.CollectionAttachment;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.UnsupportedAttachmentException;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.ConfirmAttachmentUploadUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.DeleteAttachmentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.ListAttachmentsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.RequestAttachmentUploadUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.out.AttachmentRepositoryPort;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.out.AttachmentStoragePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@ApplicationScoped
public class CollectionAttachmentService implements RequestAttachmentUploadUseCase, ConfirmAttachmentUploadUseCase,
        ListAttachmentsUseCase, DeleteAttachmentUseCase {

    @Inject
    AttachmentRepositoryPort attachmentRepository;

    @Inject
    AttachmentStoragePort attachmentStorage;

    @Override
    public UploadTicket requestUpload(String collectionId, String fileName, String contentType, long sizeBytes) {
        AttachmentPolicy.validate(contentType, sizeBytes);
        String attachmentId = UUID.randomUUID().toString();
        String s3Key = s3Key(collectionId, attachmentId);
        String uploadUrl = attachmentStorage.presignPutUrl(s3Key, contentType);
        return new UploadTicket(attachmentId, uploadUrl);
    }

    @Override
    public CollectionAttachment confirmUpload(
            String collectionId, String attachmentId, String fileName, String contentType, long sizeBytes) {
        AttachmentPolicy.validate(contentType, sizeBytes);
        String s3Key = s3Key(collectionId, attachmentId);
        if (!attachmentStorage.objectExists(s3Key)) {
            // The browser's PUT to the presigned URL never actually completed (network
            // failure, cancelled upload) - nothing to confirm.
            throw new NoSuchElementException("Upload not found for attachment " + attachmentId);
        }
        CollectionAttachment attachment = new CollectionAttachment(
                attachmentId, collectionId, fileName, contentType, sizeBytes, s3Key, Instant.now());
        return attachmentRepository.saveAttachment(attachment);
    }

    @Override
    public List<AttachmentWithViewUrl> listAttachments(String collectionId) {
        return attachmentRepository.findAttachmentsByCollectionId(collectionId).stream()
                .map(attachment -> new AttachmentWithViewUrl(
                        attachment, attachmentStorage.presignGetUrl(attachment.s3Key(), attachment.fileName())))
                .toList();
    }

    @Override
    public void deleteAttachment(String collectionId, String attachmentId) {
        CollectionAttachment attachment = attachmentRepository.findAttachment(collectionId, attachmentId)
                .orElseThrow(() -> new NoSuchElementException("No such attachment: " + attachmentId));
        attachmentStorage.deleteObject(attachment.s3Key());
        attachmentRepository.deleteAttachment(collectionId, attachmentId);
    }

    private static String s3Key(String collectionId, String attachmentId) {
        return "collections/" + collectionId + "/" + attachmentId;
    }
}

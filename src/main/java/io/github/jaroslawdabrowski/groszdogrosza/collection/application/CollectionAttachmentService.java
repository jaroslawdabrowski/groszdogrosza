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
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.out.CollectionRepositoryPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalLong;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CollectionAttachmentService implements RequestAttachmentUploadUseCase, ConfirmAttachmentUploadUseCase,
        ListAttachmentsUseCase, DeleteAttachmentUseCase {

    private static final Logger LOG = Logger.getLogger(CollectionAttachmentService.class);

    @Inject
    AttachmentRepositoryPort attachmentRepository;

    @Inject
    AttachmentStoragePort attachmentStorage;

    @Inject
    CollectionRepositoryPort collectionRepository;

    @Override
    public UploadTicket requestUpload(String classId, String collectionId, String fileName, String contentType, long sizeBytes) {
        requireCollectionInClass(classId, collectionId);
        AttachmentPolicy.validate(contentType, sizeBytes);
        String attachmentId = UUID.randomUUID().toString();
        String s3Key = s3Key(collectionId, attachmentId);
        String uploadUrl = attachmentStorage.presignPutUrl(s3Key, contentType);
        return new UploadTicket(attachmentId, uploadUrl);
    }

    @Override
    public CollectionAttachment confirmUpload(
            String classId, String collectionId, String attachmentId, String fileName, String contentType,
            long sizeBytes) {
        requireCollectionInClass(classId, collectionId);
        AttachmentPolicy.validate(contentType, sizeBytes);
        String s3Key = s3Key(collectionId, attachmentId);
        OptionalLong storedSize = attachmentStorage.objectSize(s3Key);
        if (storedSize.isEmpty()) {
            // The browser's PUT to the presigned URL never actually completed (network
            // failure, cancelled upload) - nothing to confirm.
            throw new NoSuchElementException("Upload not found for attachment " + attachmentId);
        }
        if (storedSize.getAsLong() != sizeBytes) {
            // The PUT "succeeded" but S3 holds a different number of bytes than the browser
            // said it was sending - seen for real on iPhone, where the photo arrived as a
            // 0-byte object and then showed as a broken image. Don't record a file nobody can
            // open: drop the bad object and tell the client the upload failed.
            attachmentStorage.deleteObject(s3Key);
            LOG.warnf("Rejected attachment %s (%s): declared %d bytes, S3 holds %d",
                    attachmentId, fileName, sizeBytes, storedSize.getAsLong());
            throw new UnsupportedAttachmentException("Uploaded file is incomplete: expected " + sizeBytes
                    + " bytes, got " + storedSize.getAsLong());
        }
        CollectionAttachment attachment = new CollectionAttachment(
                attachmentId, collectionId, fileName, contentType, sizeBytes, s3Key, Instant.now());
        return attachmentRepository.saveAttachment(attachment);
    }

    @Override
    public List<AttachmentWithViewUrl> listAttachments(String classId, String collectionId) {
        requireCollectionInClass(classId, collectionId);
        return attachmentRepository.findAttachmentsByCollectionId(collectionId).stream()
                .map(attachment -> new AttachmentWithViewUrl(
                        attachment, attachmentStorage.presignGetUrl(attachment.s3Key(), attachment.fileName())))
                .toList();
    }

    @Override
    public void deleteAttachment(String classId, String collectionId, String attachmentId) {
        requireCollectionInClass(classId, collectionId);
        CollectionAttachment attachment = attachmentRepository.findAttachment(collectionId, attachmentId)
                .orElseThrow(() -> new NoSuchElementException("No such attachment: " + attachmentId));
        attachmentStorage.deleteObject(attachment.s3Key());
        attachmentRepository.deleteAttachment(collectionId, attachmentId);
    }

    /** Attachments hang off a collection; the collection must belong to the caller's class -
     *  otherwise "no such collection", so another class's files can't be listed, added to or
     *  deleted by guessing a collection id. */
    private void requireCollectionInClass(String classId, String collectionId) {
        collectionRepository.findCollectionById(classId, collectionId)
                .orElseThrow(() -> new NoSuchElementException("No such collection: " + collectionId));
    }

    private static String s3Key(String collectionId, String attachmentId) {
        return "collections/" + collectionId + "/" + attachmentId;
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.ListAttachmentsUseCase.AttachmentWithViewUrl;
import java.time.Instant;

public record AttachmentResponse(
        String id, String fileName, String contentType, long sizeBytes, Instant uploadedAt, String viewUrl) {

    static AttachmentResponse from(AttachmentWithViewUrl attachmentWithViewUrl) {
        var attachment = attachmentWithViewUrl.attachment();
        return new AttachmentResponse(attachment.id(), attachment.fileName(), attachment.contentType(),
                attachment.sizeBytes(), attachment.uploadedAt(), attachmentWithViewUrl.viewUrl());
    }
}

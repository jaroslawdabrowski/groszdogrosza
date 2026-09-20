package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.RequestAttachmentUploadUseCase.UploadTicket;

public record AttachmentUploadTicketResponse(String attachmentId, String uploadUrl) {

    static AttachmentUploadTicketResponse from(UploadTicket ticket) {
        return new AttachmentUploadTicketResponse(ticket.attachmentId(), ticket.uploadUrl());
    }
}

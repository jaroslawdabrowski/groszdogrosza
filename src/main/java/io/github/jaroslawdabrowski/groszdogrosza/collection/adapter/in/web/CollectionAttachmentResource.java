package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.ConfirmAttachmentUploadUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.DeleteAttachmentUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.ListAttachmentsUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.RequestAttachmentUploadUseCase;
import io.github.jaroslawdabrowski.groszdogrosza.platform.security.AuthorizationSupport;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * Documents (photos/receipts/invoices) the treasurer attaches to a collection to prove what
 * the collected money was spent on - see CLAUDE.md, "Collection attachments". Viewing is
 * open to any authenticated parent (anyone with access to the collection itself, which every
 * logged-in parent has - see {@code CollectionResource}); uploading and deleting are
 * treasurer-only, same rule as every other money-adjacent write in this app.
 *
 * <p>Upload is two calls, not one - see {@link RequestAttachmentUploadUseCase} and
 * {@link ConfirmAttachmentUploadUseCase}'s javadoc for why: the browser first gets a
 * presigned URL and PUTs the file straight to S3, then confirms so metadata is only ever
 * written for a file that's actually there.
 */
@Path("/api/collections/{collectionId}/attachments")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CollectionAttachmentResource {

    @Inject
    RequestAttachmentUploadUseCase requestAttachmentUploadUseCase;

    @Inject
    ConfirmAttachmentUploadUseCase confirmAttachmentUploadUseCase;

    @Inject
    ListAttachmentsUseCase listAttachmentsUseCase;

    @Inject
    DeleteAttachmentUseCase deleteAttachmentUseCase;

    @Inject
    AuthorizationSupport authorizationSupport;

    @Inject
    SecurityIdentity identity;

    @GET
    public List<AttachmentResponse> list(@PathParam("collectionId") String collectionId) {
        return listAttachmentsUseCase.listAttachments(collectionId).stream().map(AttachmentResponse::from).toList();
    }

    @POST
    @Path("/upload-url")
    public AttachmentUploadTicketResponse requestUpload(
            @PathParam("collectionId") String collectionId, RequestAttachmentUploadRequest request) {
        authorizationSupport.requireTreasurer(identity);
        return AttachmentUploadTicketResponse.from(requestAttachmentUploadUseCase.requestUpload(
                collectionId, request.fileName(), request.contentType(), request.sizeBytes()));
    }

    @POST
    @Path("/{attachmentId}/confirm")
    public AttachmentResponse confirmUpload(
            @PathParam("collectionId") String collectionId, @PathParam("attachmentId") String attachmentId,
            RequestAttachmentUploadRequest request) {
        authorizationSupport.requireTreasurer(identity);
        var attachment = confirmAttachmentUploadUseCase.confirmUpload(
                collectionId, attachmentId, request.fileName(), request.contentType(), request.sizeBytes());
        // Re-fetch through the list use case to get a viewUrl in the response too, rather
        // than duplicating presign logic here.
        return listAttachmentsUseCase.listAttachments(collectionId).stream()
                .filter(a -> a.attachment().id().equals(attachment.id()))
                .findFirst()
                .map(AttachmentResponse::from)
                .orElseThrow();
    }

    @DELETE
    @Path("/{attachmentId}")
    public void delete(@PathParam("collectionId") String collectionId, @PathParam("attachmentId") String attachmentId) {
        authorizationSupport.requireTreasurer(identity);
        deleteAttachmentUseCase.deleteAttachment(collectionId, attachmentId);
    }
}

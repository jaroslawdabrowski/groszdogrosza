package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.UnsupportedAttachmentException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/** Maps a rejected upload (wrong file type / too large - see {@code AttachmentPolicy}) to 400 Bad Request. */
@Provider
public class UnsupportedAttachmentExceptionMapper implements ExceptionMapper<UnsupportedAttachmentException> {

    @Override
    public Response toResponse(UnsupportedAttachmentException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "attachment.unsupported", "message", exception.getMessage()))
                .build();
    }
}

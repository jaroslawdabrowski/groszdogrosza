package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.UnsupportedAttachmentException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import org.jboss.logging.Logger;

/** Maps a rejected upload (wrong file type / too large - see {@code AttachmentPolicy}) to 400 Bad Request. */
@Provider
public class UnsupportedAttachmentExceptionMapper implements ExceptionMapper<UnsupportedAttachmentException> {

    private static final Logger LOG = Logger.getLogger(UnsupportedAttachmentExceptionMapper.class);

    @Override
    public Response toResponse(UnsupportedAttachmentException exception) {
        // Logged because the browser only ever shows a generic "upload failed" - the reason
        // has to be findable in CloudWatch.
        LOG.warnf("Attachment rejected: %s", exception.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "attachment.unsupported", "message", exception.getMessage()))
                .build();
    }
}

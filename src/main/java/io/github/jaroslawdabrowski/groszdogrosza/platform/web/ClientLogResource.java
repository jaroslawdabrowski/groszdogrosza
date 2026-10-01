package io.github.jaroslawdabrowski.groszdogrosza.platform.web;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

/**
 * Lets the frontend write a failure that only happens in the browser into the Lambda's own
 * CloudWatch log. Added for attachment uploads from an iPhone, which fail in steps the server
 * never sees (reading the picked photo, the direct PUT to S3): without this the only symptom
 * was a generic "upload failed" on the phone and nothing at all in the logs. Logged-in users
 * only, and the text is length-capped, so it can't be used to flood the log anonymously.
 */
@Path("/api/client-log")
@Authenticated
public class ClientLogResource {

    private static final Logger LOG = Logger.getLogger(ClientLogResource.class);
    private static final int MAX_LENGTH = 1000;

    public record ClientLogRequest(String context, String message) {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void log(ClientLogRequest request, @HeaderParam("User-Agent") String userAgent) {
        if (request == null) {
            return;
        }
        LOG.warnf("Client error [%s]: %s | %s", cap(request.context()), cap(request.message()), cap(userAgent));
    }

    private static String cap(String value) {
        if (value == null) {
            return "-";
        }
        String oneLine = value.replaceAll("[\\r\\n]+", " ");
        return oneLine.length() > MAX_LENGTH ? oneLine.substring(0, MAX_LENGTH) + "..." : oneLine;
    }
}

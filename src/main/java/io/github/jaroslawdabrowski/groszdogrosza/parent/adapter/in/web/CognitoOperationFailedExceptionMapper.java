package io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.CognitoOperationFailedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/** 502 - the request itself was fine, an upstream AWS call (or missing config) failed. */
@Provider
public class CognitoOperationFailedExceptionMapper implements ExceptionMapper<CognitoOperationFailedException> {

    @Override
    public Response toResponse(CognitoOperationFailedException exception) {
        return Response.status(Response.Status.BAD_GATEWAY)
                .entity(Map.of("error", "parent.cognitoOperationFailed", "message", exception.getMessage()))
                .build();
    }
}

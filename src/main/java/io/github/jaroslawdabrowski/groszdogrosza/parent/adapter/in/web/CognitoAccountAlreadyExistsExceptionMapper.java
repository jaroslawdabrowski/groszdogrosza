package io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.CognitoAccountAlreadyExistsException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class CognitoAccountAlreadyExistsExceptionMapper implements ExceptionMapper<CognitoAccountAlreadyExistsException> {

    @Override
    public Response toResponse(CognitoAccountAlreadyExistsException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", "parent.cognitoAccountAlreadyExists", "message", exception.getMessage()))
                .build();
    }
}

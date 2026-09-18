package io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.TooManyParentsException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class TooManyParentsExceptionMapper implements ExceptionMapper<TooManyParentsException> {

    @Override
    public Response toResponse(TooManyParentsException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", "student.tooManyParents", "message", exception.getMessage()))
                .build();
    }
}

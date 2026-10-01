package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.InvalidCollectionException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/** Maps a rejected new collection (see {@code NewCollectionPolicy}) to 400 Bad Request. */
@Provider
public class InvalidCollectionExceptionMapper implements ExceptionMapper<InvalidCollectionException> {

    @Override
    public Response toResponse(InvalidCollectionException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "collection.invalid", "message", exception.getMessage()))
                .build();
    }
}

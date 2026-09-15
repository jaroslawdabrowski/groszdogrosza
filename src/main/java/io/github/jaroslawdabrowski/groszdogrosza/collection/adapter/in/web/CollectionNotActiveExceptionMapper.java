package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.CollectionNotActiveException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/**
 * Maps a rejected double-settle (see {@code CollectionNotActiveException}) to 409 Conflict -
 * the collection exists (not 404) but the request conflicts with its current state.
 */
@Provider
public class CollectionNotActiveExceptionMapper implements ExceptionMapper<CollectionNotActiveException> {

    @Override
    public Response toResponse(CollectionNotActiveException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", "collection.notActive", "message", exception.getMessage()))
                .build();
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.StudentAlreadyInCollectionException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/** Maps a rejected re-add (see StudentAlreadyInCollectionException) to 409 Conflict - same pattern as CollectionNotActiveExceptionMapper. */
@Provider
public class StudentAlreadyInCollectionExceptionMapper implements ExceptionMapper<StudentAlreadyInCollectionException> {

    @Override
    public Response toResponse(StudentAlreadyInCollectionException exception) {
        return Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", "collection.studentAlreadyIncluded", "message", exception.getMessage()))
                .build();
    }
}

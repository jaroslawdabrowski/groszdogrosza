package io.github.jaroslawdabrowski.groszdogrosza.platform.web;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.NoSuchElementException;
import java.util.Map;

/**
 * Every application service in this app signals "no such id" the same way -
 * {@code NoSuchElementException} - whether it's a missing parent or a missing collection.
 * Without this mapper it falls through to Quarkus's default unmapped-exception handling,
 * which returns an opaque 500 instead of the 404 a client should get. GET endpoints already
 * translate a missing id into JAX-RS's own {@code NotFoundException} explicitly before this
 * mapper would ever see it; this exists for the POST endpoints (record a contribution,
 * settle) that don't.
 */
@Provider
public class NoSuchElementExceptionMapper implements ExceptionMapper<NoSuchElementException> {

    @Override
    public Response toResponse(NoSuchElementException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "notFound", "message", exception.getMessage()))
                .build();
    }
}

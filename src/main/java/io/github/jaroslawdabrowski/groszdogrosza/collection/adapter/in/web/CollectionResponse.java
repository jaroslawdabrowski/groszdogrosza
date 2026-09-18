package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import java.math.BigDecimal;
import java.time.Instant;

public record CollectionResponse(
        String id, String title, String description, String status,
        BigDecimal baseAmountPerStudent, Instant createdAt) {

    static CollectionResponse from(Collection collection) {
        return new CollectionResponse(collection.id(), collection.title(), collection.description(),
                collection.status().name(), collection.baseAmountPerStudent(), collection.createdAt());
    }
}

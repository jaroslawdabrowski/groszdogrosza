package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param myStudentStatus The caller's OWN child's status in this collection - one of
 *     {@code ContributionRequirementStatus}'s names ({@code PENDING}/{@code PAID}/
 *     {@code OVERPAID}) if included, {@code "NOT_INCLUDED"} if the collection exists but
 *     the caller's child isn't in it, or {@code null} if there's no logged-in parent (or no
 *     Parent record linked to a Student yet) to resolve "own child" for in the first place.
 *     Never another family's status - see {@code CollectionResource.list}/{@code get} and
 *     {@code CollectionProgressResponse.from}, the only places this is actually computed;
 *     every other caller of {@link #from(Collection)} leaves it {@code null} since it isn't
 *     needed there (the treasurer's own per-student table already shows everyone's status).
 */
public record CollectionResponse(
        String id, String title, String description, String status,
        BigDecimal baseAmountPerStudent, Instant createdAt, String myStudentStatus) {

    static CollectionResponse from(Collection collection) {
        return from(collection, null);
    }

    static CollectionResponse from(Collection collection, String myStudentStatus) {
        return new CollectionResponse(collection.id(), collection.title(), collection.description(),
                collection.status().name(), collection.baseAmountPerStudent(), collection.createdAt(), myStudentStatus);
    }
}

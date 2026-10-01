package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The {@code myStudent*} fields describe the caller's OWN child in this collection - never
 * another family's. They're {@code null} when there's no logged-in parent (or no Parent
 * record linked to a Student yet) to resolve "own child" for; {@link #forViewer} is the one
 * place they're computed, and every other caller of {@link #from(Collection)} leaves them
 * {@code null} (the treasurer's per-student list already shows everyone).
 *
 * @param myStudentStatus one of {@code ContributionRequirementStatus}'s names
 *     ({@code PENDING}/{@code PAID}/{@code OVERPAID}) if the child is included, or
 *     {@code "NOT_INCLUDED"} if the collection exists but the child isn't in it.
 * @param myStudentRequiredAmount what this collection asks of the child (null when not
 *     included) - lets a parent's home screen say "Kalina ma do wpłaty 27 zł".
 * @param myStudentPaidAmount what's been paid for the child so far, piggy-bank sweeps
 *     included (null when not included).
 */
public record CollectionResponse(
        String id, String title, String description, String status,
        BigDecimal baseAmountPerStudent, Instant createdAt,
        String myStudentStatus, BigDecimal myStudentRequiredAmount, BigDecimal myStudentPaidAmount) {

    static CollectionResponse from(Collection collection) {
        return new CollectionResponse(collection.id(), collection.title(), collection.description(),
                collection.status().name(), collection.baseAmountPerStudent(), collection.createdAt(), null, null, null);
    }

    /** @param myStudentId the caller's own child ({@code Parent.studentId}), or null. */
    static CollectionResponse forViewer(Collection collection, List<ContributionRequirement> requirements, String myStudentId) {
        if (myStudentId == null) {
            return from(collection);
        }
        ContributionRequirement mine = requirements.stream()
                .filter(r -> r.studentId().equals(myStudentId))
                .findFirst()
                .orElse(null);
        return new CollectionResponse(collection.id(), collection.title(), collection.description(),
                collection.status().name(), collection.baseAmountPerStudent(), collection.createdAt(),
                mine == null ? "NOT_INCLUDED" : mine.status().name(),
                mine == null ? null : mine.requiredAmount(),
                mine == null ? null : mine.paidAmount());
    }
}

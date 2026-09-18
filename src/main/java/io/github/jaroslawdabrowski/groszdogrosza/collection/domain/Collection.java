package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single money collection for the class (e.g. a teacher's end-of-year gift).
 *
 * @param baseAmountPerStudent the nominal amount every student is asked for, before their
 *                             individual piggy bank balance is deducted - see
 *                             {@code ContributionRequirement}, which is where that deduction
 *                             actually happens, once per student, when the collection is
 *                             activated.
 */
public record Collection(
        String id,
        String title,
        String description,
        CollectionStatus status,
        BigDecimal baseAmountPerStudent,
        Instant createdAt) {

    public Collection {
        if (baseAmountPerStudent.signum() < 0) {
            throw new IllegalArgumentException("baseAmountPerStudent cannot be negative: " + baseAmountPerStudent);
        }
    }

    public Collection withStatus(CollectionStatus newStatus) {
        return new Collection(id, title, description, newStatus, baseAmountPerStudent, createdAt);
    }
}

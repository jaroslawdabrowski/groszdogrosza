package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single money collection for the class (e.g. a teacher's end-of-year gift).
 *
 * @param baseAmountPerParent the nominal amount every parent is asked for, before their
 *                            individual piggy bank balance is deducted - see
 *                            {@code ContributionRequirement}, which is where that deduction
 *                            actually happens, once per parent, when the collection is
 *                            activated.
 */
public record Collection(
        String id,
        String title,
        String description,
        CollectionStatus status,
        BigDecimal baseAmountPerParent,
        Instant createdAt) {

    public Collection {
        if (baseAmountPerParent.signum() < 0) {
            throw new IllegalArgumentException("baseAmountPerParent cannot be negative: " + baseAmountPerParent);
        }
    }

    public Collection withStatus(CollectionStatus newStatus) {
        return new Collection(id, title, description, newStatus, baseAmountPerParent, createdAt);
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import java.math.BigDecimal;

/**
 * Creates a collection and immediately activates it: a {@code ContributionRequirement} is
 * computed for every currently-known parent right away (baseAmountPerParent minus that
 * parent's piggy bank balance at this moment, floored at zero). There is deliberately no
 * separate "activate a draft" step in this scaffolding phase - {@code CollectionStatus.DRAFT}
 * exists in the domain model for a future two-step flow (e.g. let the treasurer preview
 * requirements before committing), but nothing currently produces one.
 */
public interface CreateCollectionUseCase {

    Collection createCollection(String title, String description, BigDecimal baseAmountPerParent);
}

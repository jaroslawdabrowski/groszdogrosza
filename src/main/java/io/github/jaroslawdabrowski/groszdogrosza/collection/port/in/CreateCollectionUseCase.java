package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Collection;
import java.math.BigDecimal;
import java.util.List;

/**
 * Creates a collection and immediately activates it: a {@code ContributionRequirement} is
 * computed for every student in {@code includedStudentIds} right away (baseAmountPerStudent
 * minus that student's piggy bank balance at this moment, floored at zero). There is
 * deliberately no separate "activate a draft" step in this scaffolding phase -
 * {@code CollectionStatus.DRAFT} exists in the domain model for a future two-step flow (e.g.
 * let the treasurer preview requirements before committing), but nothing currently produces
 * one.
 *
 * <p>{@code includedStudentIds} exists because not every collection is "everyone in the
 * class owes money" - a class trip everyone is invited to, but one family already told the
 * treasurer their kid isn't coming, shouldn't produce a requirement (and a nagging "Do
 * zapłaty" row) for a student who was never asked to pay. The treasurer picks who's in from
 * a checklist that starts with every student checked - see {@code TreasurerPanel}.
 */
public interface CreateCollectionUseCase {

    Collection createCollection(
            String title, String description, BigDecimal baseAmountPerStudent, List<String> includedStudentIds);
}

package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirementStatus;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase.CollectionDetails;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What a regular (non-treasurer) parent is allowed to see about a collection: aggregate
 * progress only - never the per-parent requirement/contribution breakdown that
 * {@link CollectionDetailsResponse} carries, since that would expose every other family's
 * payment status and amounts. See {@code CollectionResource.get} for the role check that
 * picks between the two.
 */
public record CollectionProgressResponse(
        CollectionResponse collection,
        int parentsCount,
        int parentsPaidCount,
        BigDecimal totalRequired,
        BigDecimal totalPaid,
        int percentComplete) {

    static CollectionProgressResponse from(CollectionDetails details) {
        BigDecimal totalRequired = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        int paidCount = 0;
        for (ContributionRequirement requirement : details.requirements()) {
            totalRequired = totalRequired.add(requirement.requiredAmount());
            totalPaid = totalPaid.add(requirement.paidAmount());
            if (requirement.status() != ContributionRequirementStatus.PENDING) {
                paidCount++;
            }
        }
        int percent = totalRequired.signum() == 0
                ? 100
                : totalPaid.min(totalRequired).multiply(BigDecimal.valueOf(100))
                        .divide(totalRequired, 0, RoundingMode.DOWN)
                        .intValue();
        return new CollectionProgressResponse(CollectionResponse.from(details.collection()),
                details.requirements().size(), paidCount, totalRequired, totalPaid, percent);
    }
}

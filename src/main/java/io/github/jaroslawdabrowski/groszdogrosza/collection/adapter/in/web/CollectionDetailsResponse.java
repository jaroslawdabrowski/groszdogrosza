package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Contribution;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase.CollectionDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CollectionDetailsResponse(
        CollectionResponse collection,
        List<RequirementResponse> requirements,
        List<ContributionResponse> contributions) {

    static CollectionDetailsResponse from(CollectionDetails details) {
        return new CollectionDetailsResponse(
                CollectionResponse.from(details.collection()),
                details.requirements().stream().map(RequirementResponse::from).toList(),
                details.contributions().stream().map(ContributionResponse::from).toList());
    }

    public record RequirementResponse(
            String parentId, BigDecimal requiredAmount, BigDecimal paidAmount, String status) {

        static RequirementResponse from(ContributionRequirement requirement) {
            return new RequirementResponse(requirement.parentId(), requirement.requiredAmount(),
                    requirement.paidAmount(), requirement.status().name());
        }
    }

    public record ContributionResponse(
            String id, String parentId, BigDecimal amount, String source, Instant receivedAt) {

        static ContributionResponse from(Contribution contribution) {
            return new ContributionResponse(contribution.id(), contribution.parentId(), contribution.amount(),
                    contribution.source().name(), contribution.receivedAt());
        }
    }
}

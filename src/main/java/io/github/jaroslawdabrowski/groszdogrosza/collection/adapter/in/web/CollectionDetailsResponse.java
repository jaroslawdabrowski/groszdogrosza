package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Contribution;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase.CollectionDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CollectionDetailsResponse(
        CollectionResponse collection,
        List<RequirementResponse> requirements,
        List<ContributionResponse> contributions) {

    /** @param studentNamesById resolved by the caller (see CollectionResource) - this DTO
     *  layer has no DI access of its own, and a per-collection breakdown reads much better
     *  with names than with raw ids (see the "TODO: show names, not UUIDs" note this closes). */
    static CollectionDetailsResponse from(CollectionDetails details, Map<String, String> studentNamesById) {
        return new CollectionDetailsResponse(
                CollectionResponse.from(details.collection()),
                details.requirements().stream().map(r -> RequirementResponse.from(r, studentNamesById)).toList(),
                details.contributions().stream().map(c -> ContributionResponse.from(c, studentNamesById)).toList());
    }

    public record RequirementResponse(
            String studentId, String studentName, BigDecimal requiredAmount, BigDecimal paidAmount, String status) {

        static RequirementResponse from(ContributionRequirement requirement, Map<String, String> studentNamesById) {
            return new RequirementResponse(requirement.studentId(),
                    studentNamesById.getOrDefault(requirement.studentId(), requirement.studentId()),
                    requirement.requiredAmount(), requirement.paidAmount(), requirement.status().name());
        }
    }

    public record ContributionResponse(
            String id, String studentId, String studentName, BigDecimal amount, String source, Instant receivedAt) {

        static ContributionResponse from(Contribution contribution, Map<String, String> studentNamesById) {
            return new ContributionResponse(contribution.id(), contribution.studentId(),
                    studentNamesById.getOrDefault(contribution.studentId(), contribution.studentId()),
                    contribution.amount(), contribution.source().name(), contribution.receivedAt());
        }
    }
}

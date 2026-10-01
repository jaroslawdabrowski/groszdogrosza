package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirementStatus;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase.CollectionDetails;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What a regular (non-treasurer) parent is allowed to see about a collection: aggregate
 * progress only - never the per-student requirement/contribution breakdown that
 * {@link CollectionDetailsResponse} carries, since that would expose every other family's
 * payment status and amounts. See {@code CollectionResource.get} for the role check that
 * picks between the two. Also reused (public {@link #from}) by
 * {@code platform.web.PublicOverviewResource} for the unauthenticated public page, which
 * shows this same aggregate-only shape for every currently ACTIVE collection.
 *
 * <p>A plain sum of {@code requiredAmount}/{@code paidAmount} across every requirement is
 * correct here (unlike an earlier version of this class) because
 * {@code CollectionService.createCollection} now sweeps a pre-existing piggy bank balance
 * into a real {@code Contribution} at creation time, rather than just silently discounting
 * {@code requiredAmount} with no corresponding {@code paidAmount} - see
 * {@code ContributionRequirement}'s javadoc. {@code requiredAmount} is always the
 * collection's nominal {@code baseAmountPerStudent}, so summing it across every included
 * student naturally gives the collection's true total value, and {@code paidAmount} already
 * reflects everything genuinely covered (from an existing balance, a bank transfer, or a
 * manual entry) - no separate reconstruction needed.
 */
public record CollectionProgressResponse(
        CollectionResponse collection,
        int studentsCount,
        int studentsPaidCount,
        int studentsPartialCount,
        BigDecimal totalRequired,
        BigDecimal totalPaid,
        int percentComplete) {

    public static CollectionProgressResponse from(CollectionDetails details) {
        return from(details, null);
    }

    /**
     * @param myStudentId the caller's own child's id (from {@code Parent.studentId}), or
     *     {@code null} if there's no logged-in parent (or no linked Student) - see
     *     {@code CollectionResponse}'s javadoc for what the {@code myStudent*} fields mean.
     *     {@code studentsPartialCount} counts students who've paid something but not all of
     *     it - with {@code studentsPaidCount} it drives the class roster dots (one per
     *     student: filled, half-filled or empty). Counts only, never whose.
     */
    public static CollectionProgressResponse from(CollectionDetails details, String myStudentId) {
        BigDecimal totalRequired = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        int paidCount = 0;
        int partialCount = 0;
        for (ContributionRequirement requirement : details.requirements()) {
            totalRequired = totalRequired.add(requirement.requiredAmount());
            totalPaid = totalPaid.add(requirement.paidAmount());
            if (requirement.status() != ContributionRequirementStatus.PENDING) {
                paidCount++;
            } else if (requirement.paidAmount().signum() > 0) {
                partialCount++;
            }
        }
        int percent = totalRequired.signum() == 0
                ? 100
                : totalPaid.min(totalRequired).multiply(BigDecimal.valueOf(100))
                        .divide(totalRequired, 0, RoundingMode.DOWN)
                        .intValue();
        return new CollectionProgressResponse(
                CollectionResponse.forViewer(details.collection(), details.requirements(), myStudentId),
                details.requirements().size(), paidCount, partialCount, totalRequired, totalPaid, percent);
    }
}

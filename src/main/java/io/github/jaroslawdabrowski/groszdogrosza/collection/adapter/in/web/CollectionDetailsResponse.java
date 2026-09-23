package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.Contribution;
import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import io.github.jaroslawdabrowski.groszdogrosza.collection.port.in.GetCollectionUseCase.CollectionDetails;
import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CollectionDetailsResponse(
        CollectionResponse collection,
        List<RequirementResponse> requirements,
        List<ContributionResponse> contributions,
        int removedStudentsCount) {

    /**
     * @param studentsInOrder resolved by the caller (see CollectionResource), already
     *  sorted lastName-then-firstName (see {@code ListStudentsUseCase.listStudents}) - this
     *  DTO layer has no DI access of its own. Used both to resolve names (a per-collection
     *  breakdown reads much better with names than raw ids - the "TODO: show names, not
     *  UUIDs" note this closes) and to put the requirements table in that same roster order
     *  instead of whatever order the underlying DynamoDB Query happened to return.
     * @param myStudentId the treasurer's OWN child's id (see
     *  {@code CollectionResponse#myStudentStatus}'s javadoc) - a treasurer is also a Parent
     *  with a linked Student, and wants the same quick join/leave affordance on their own
     *  collection page a regular parent gets, not just the full per-student table (which
     *  works, but means hunting their own row out of a potentially long roster). {@code null}
     *  if the treasurer's own account has no linked Student yet.
     * @param removedStudentsCount resolved by the caller from the global ledger (see
     *  CollectionResource) - {@code RemoveStudentFromCollectionUseCase} DELETES a removed
     *  student's requirement/contributions outright (see that class's own javadoc: "must
     *  vanish from this collection's breakdown entirely"), so by the time this DTO is built
     *  there is no trace of them left in {@code details.requirements()}/{@code contributions()}
     *  at all - the ONLY remaining record is the {@code REMOVED_FROM_COLLECTION} ledger entry
     *  each removal writes. Shown on the settle card so the treasurer can see at a glance how
     *  many students dropped out before finalizing the numbers.
     */
    static CollectionDetailsResponse from(
            CollectionDetails details, List<Student> studentsInOrder, String myStudentId, int removedStudentsCount) {
        Map<String, String> studentNamesById = new LinkedHashMap<>();
        for (Student student : studentsInOrder) {
            studentNamesById.put(student.id(), student.fullName());
        }

        Map<String, ContributionRequirement> requirementsByStudentId = new LinkedHashMap<>();
        for (ContributionRequirement requirement : details.requirements()) {
            requirementsByStudentId.put(requirement.studentId(), requirement);
        }
        List<RequirementResponse> orderedRequirements = new ArrayList<>();
        for (Student student : studentsInOrder) {
            ContributionRequirement requirement = requirementsByStudentId.remove(student.id());
            if (requirement != null) {
                orderedRequirements.add(RequirementResponse.from(requirement, studentNamesById));
            }
        }
        // A requirement whose student isn't in the current roster (shouldn't normally
        // happen) still shows up, just not name-sorted - never silently drop real data.
        for (ContributionRequirement requirement : requirementsByStudentId.values()) {
            orderedRequirements.add(RequirementResponse.from(requirement, studentNamesById));
        }

        String myStudentStatus = myStudentId == null ? null : details.requirements().stream()
                .filter(r -> r.studentId().equals(myStudentId))
                .findFirst()
                .map(r -> r.status().name())
                .orElse("NOT_INCLUDED");

        return new CollectionDetailsResponse(
                CollectionResponse.from(details.collection(), myStudentStatus),
                orderedRequirements,
                details.contributions().stream().map(c -> ContributionResponse.from(c, studentNamesById)).toList(),
                removedStudentsCount);
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

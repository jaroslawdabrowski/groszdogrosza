package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import java.util.List;

/**
 * Internal port used by {@code bankstatement.application.BankStatementProcessingService}
 * to find out what a student's family still owes across every currently ACTIVE collection,
 * so {@code ContributionAllocationPolicy} can decide how much of an incoming payment (or
 * existing piggy bank balance) to sweep towards them automatically.
 */
public interface GetActiveRequirementsForStudentUseCase {

    List<ContributionRequirement> getActivePendingRequirements(String studentId);
}

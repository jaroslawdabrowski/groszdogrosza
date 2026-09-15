package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.collection.domain.ContributionRequirement;
import java.util.List;

/**
 * Internal port used by {@code bankstatement.application.BankStatementProcessingService}
 * to find out what a parent still owes across every currently ACTIVE collection, so
 * {@code ContributionAllocationPolicy} can decide how much of an incoming payment (or
 * existing piggy bank balance) to sweep towards them automatically.
 */
public interface GetActiveRequirementsForParentUseCase {

    List<ContributionRequirement> getActivePendingRequirements(String parentId);
}

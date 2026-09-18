package io.github.jaroslawdabrowski.groszdogrosza.parent.port.in;

/**
 * Treasurer-only: permanently remove a parent record. Deliberately does NOT cascade - any
 * {@code ContributionRequirement}/{@code Contribution}/{@code LedgerEntry} already recorded
 * against this parent id stays exactly as it was (this app has no referential-integrity
 * layer, matching every other cross-aggregate reference here); a collection's totals still
 * reflect money that was actually paid. This is meant for cleaning up a duplicate or
 * never-should-have-existed record, not for "offboarding" a parent with real history.
 */
public interface DeleteParentUseCase {

    void deleteParent(String parentId);
}

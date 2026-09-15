package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

public enum CollectionStatus {
    /** Created but not yet opened for contributions - requirements not computed yet. */
    DRAFT,
    /** Open for contributions; requirements are fixed and contributions are being tracked. */
    ACTIVE,
    /** Settled: actual cost recorded, SettlementPolicy applied, leftovers credited to piggy banks. */
    SETTLED
}

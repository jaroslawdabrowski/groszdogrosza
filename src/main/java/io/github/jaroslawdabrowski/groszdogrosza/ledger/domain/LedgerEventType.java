package io.github.jaroslawdabrowski.groszdogrosza.ledger.domain;

/**
 * What happened, not how it reads - rendering an event into a sentence is the frontend's
 * job via i18n (key {@code ledger.<NAME>} in en.json/pl.json, with placeholders matching
 * whatever keys {@link LedgerEntry#params()} carries for that event type). Mirrors the
 * DecisionReason pattern from the sibling "pvopt" project: keep the enum + params, never a
 * pre-rendered message, so translation and phrasing can change without touching stored data.
 */
public enum LedgerEventType {
    /** A contribution was recorded against a collection. params: collectionId, collectionTitle, amount. */
    CONTRIBUTION_RECEIVED,
    /** A collection was settled and this parent's leftover share was computed. params: collectionId, collectionTitle, leftoverAmount. */
    COLLECTION_SETTLED,
    /** Money landed in this parent's piggy bank (bank transfer surplus, or a settlement leftover). params: amount, bankReference (optional). */
    PIGGY_BANK_CREDITED,
    /** Piggy bank balance was applied towards an active collection's requirement. params: collectionId, collectionTitle, amount. */
    PIGGY_BANK_APPLIED_TO_COLLECTION
}

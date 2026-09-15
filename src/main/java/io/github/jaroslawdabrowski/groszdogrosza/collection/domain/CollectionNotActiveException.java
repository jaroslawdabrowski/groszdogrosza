package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

/**
 * Thrown when an operation that requires an ACTIVE collection (currently only
 * {@code settleCollection}) is attempted on one that is already SETTLED (or, once a
 * draft/activate flow exists, still DRAFT). Guards against settling the same collection
 * twice - e.g. a double-clicked "Settle" button or a client retry after a timeout - which
 * would otherwise re-credit every contributing parent's leftover a second time.
 */
public class CollectionNotActiveException extends RuntimeException {

    public CollectionNotActiveException(String collectionId, CollectionStatus actualStatus) {
        super("Collection " + collectionId + " is not ACTIVE (status: " + actualStatus + ")");
    }
}

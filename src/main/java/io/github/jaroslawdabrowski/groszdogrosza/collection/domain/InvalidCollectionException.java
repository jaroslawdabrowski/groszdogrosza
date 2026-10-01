package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

/** Thrown when a new collection violates {@link NewCollectionPolicy}. */
public class InvalidCollectionException extends RuntimeException {

    public InvalidCollectionException(String message) {
        super(message);
    }
}

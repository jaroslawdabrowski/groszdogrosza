package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

/** Thrown when a requested attachment upload violates {@link AttachmentPolicy}. */
public class UnsupportedAttachmentException extends RuntimeException {

    public UnsupportedAttachmentException(String message) {
        super(message);
    }
}

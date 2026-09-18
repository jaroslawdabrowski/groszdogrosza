package io.github.jaroslawdabrowski.groszdogrosza.parent.domain;

/** A student already has 2 parents - see {@code Parent}'s javadoc for why that's the cap. */
public class TooManyParentsException extends RuntimeException {

    public TooManyParentsException(String studentId) {
        super("Student " + studentId + " already has 2 parents");
    }
}

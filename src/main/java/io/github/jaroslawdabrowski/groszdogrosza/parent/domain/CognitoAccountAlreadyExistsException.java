package io.github.jaroslawdabrowski.groszdogrosza.parent.domain;

/**
 * Thrown when the treasurer tries to create a login account for a parent who already has
 * one - Cognito's {@code AdminCreateUser} rejects a duplicate username (the pool is
 * configured with {@code username_attributes = ["email"]}, so the username IS the email).
 * The fix for "they already have an account but say they never got the invite" is
 * {@code ResendCognitoInvitationUseCase}, not calling create again.
 */
public class CognitoAccountAlreadyExistsException extends RuntimeException {

    public CognitoAccountAlreadyExistsException(String email) {
        super("A login account already exists for " + email);
    }
}

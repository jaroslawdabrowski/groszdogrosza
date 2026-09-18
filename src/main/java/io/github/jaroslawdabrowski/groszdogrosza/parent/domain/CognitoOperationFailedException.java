package io.github.jaroslawdabrowski.groszdogrosza.parent.domain;

/**
 * Wraps any Cognito admin-API failure that isn't the specific "account already exists"
 * case - e.g. the user pool isn't configured in this environment (local dev has no real
 * Cognito pool at all), or a resend was attempted for a parent who already confirmed their
 * account and set a real password (Cognito's {@code RESEND} message action only works while
 * a user is still in the {@code FORCE_CHANGE_PASSWORD} state).
 */
public class CognitoOperationFailedException extends RuntimeException {

    public CognitoOperationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

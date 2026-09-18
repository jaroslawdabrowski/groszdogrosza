package io.github.jaroslawdabrowski.groszdogrosza.parent.port.out;

/**
 * Admin operations against the Cognito User Pool a parent logs in through - separate from
 * {@code platform.security} (which only ever verifies an already-issued token) since this is
 * account *management*, not authentication. See {@code parent.adapter.out.cognito} for the
 * implementation and why these two calls, specifically, exist.
 */
public interface CognitoAccountManagementPort {

    /** Creates a login account for this email; Cognito auto-generates a temporary password
     *  and emails it via its built-in invitation message - see the adapter's javadoc for why
     *  no password is generated here. */
    void createAccount(String email);

    /** Re-sends the invitation email (with a freshly generated temporary password) for an
     *  account that was already created but hasn't been confirmed yet - "I didn't get the
     *  email" support case. */
    void resendInvitation(String email);
}

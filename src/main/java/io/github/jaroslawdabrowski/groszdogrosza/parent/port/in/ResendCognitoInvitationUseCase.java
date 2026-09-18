package io.github.jaroslawdabrowski.groszdogrosza.parent.port.in;

/** Treasurer-only: re-send the login-account invitation email for a parent who already has
 *  an account but says they never received it (or it expired). */
public interface ResendCognitoInvitationUseCase {

    void resendCognitoInvitation(String parentId);
}

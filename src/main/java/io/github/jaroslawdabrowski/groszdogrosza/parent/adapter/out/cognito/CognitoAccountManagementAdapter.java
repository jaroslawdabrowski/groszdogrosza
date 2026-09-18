package io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.out.cognito;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.CognitoAccountAlreadyExistsException;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.CognitoOperationFailedException;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.out.CognitoAccountManagementPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeliveryMediumType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

/**
 * Both operations here call the exact same underlying API, {@code AdminCreateUser} - Cognito
 * overloads it via {@code MessageAction}: omitted (default) creates a brand new user and
 * sends the welcome/invitation email; {@code RESEND} re-sends that same invitation (with a
 * freshly generated temporary password) for a user that already exists but hasn't confirmed
 * yet. Neither call passes an explicit {@code TemporaryPassword} - leaving it out makes
 * Cognito generate one itself and include it directly in the email it sends, which is
 * simpler and safer than generating one here and having to get it to the parent through some
 * other channel.
 *
 * <p>The pool has no custom {@code email_configuration} in Terraform (see
 * {@code aws_cognito_user_pool.app} in infra/main/main.tf), so these emails go out through
 * Cognito's own built-in, sender-address-you-don't-control mailer - fine for a class of ~20
 * parents, but worth revisiting (a real SES domain) if this app ever needs a branded sender
 * or higher sending volume than Cognito's default service allows.
 */
@ApplicationScoped
public class CognitoAccountManagementAdapter implements CognitoAccountManagementPort {

    @Inject
    CognitoIdentityProviderClient cognitoClient;

    /** Empty by default like every other environment-specific config in this app
     *  (see {@code ImapBankStatementFetchAdapter}'s javadoc for why this must stay
     *  {@code Optional<String>}, not a plain required {@code String} - a required
     *  {@code @ConfigProperty} with no value crashes Quarkus's entire boot, not just this
     *  bean, the moment CDI tries to inject it). Set via
     *  {@code GROSZDOGROSZA_COGNITO_USER_POOL_ID} in the real deployment. */
    @ConfigProperty(name = "groszdogrosza.cognito.user-pool-id")
    Optional<String> userPoolId;

    @Override
    public void createAccount(String email) {
        String poolId = requirePoolId();
        try {
            cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(poolId)
                    .username(email)
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build(),
                            AttributeType.builder().name("email_verified").value("true").build())
                    .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
                    .build());
        } catch (UsernameExistsException e) {
            throw new CognitoAccountAlreadyExistsException(email);
        } catch (CognitoIdentityProviderException e) {
            throw new CognitoOperationFailedException("Failed to create a login account for " + email, e);
        }
    }

    @Override
    public void resendInvitation(String email) {
        String poolId = requirePoolId();
        try {
            cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(poolId)
                    .username(email)
                    .messageAction(MessageActionType.RESEND)
                    .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
                    .build());
        } catch (CognitoIdentityProviderException e) {
            // Most common real-world cause: the parent already confirmed their account and
            // set a real password, so there's no pending invitation left to resend - Cognito
            // rejects RESEND once a user is out of the FORCE_CHANGE_PASSWORD state.
            throw new CognitoOperationFailedException(
                    "Failed to resend the invitation to " + email
                            + " - if they already logged in and set a password, there's nothing to resend", e);
        }
    }

    private String requirePoolId() {
        return userPoolId.filter(id -> !id.isBlank())
                .orElseThrow(() -> new CognitoOperationFailedException(
                        "groszdogrosza.cognito.user-pool-id is not configured in this environment", null));
    }
}

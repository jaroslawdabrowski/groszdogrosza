package io.github.jaroslawdabrowski.groszdogrosza.platform.security;

import io.quarkus.security.identity.SecurityIdentity;
import java.util.Locale;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * The logged-in user's identity, as this app understands it: the token's {@code email} claim,
 * but only when the token also says that email is verified. Identity in this app IS the
 * email (it's matched against treasurer memberships and parent records), so an unverified
 * email must never count - otherwise someone who managed to change their own account email
 * to another person's address would become that person. Every account is created verified
 * (see CognitoAccountManagementAdapter and scripts/deploy.sh), and Cognito keeps the old
 * address until a new one is verified (user_attribute_update_settings in infra/main/main.tf).
 */
public final class CallerEmail {

    private CallerEmail() {
    }

    /** Lowercased verified email, or null when there's no usable identity. */
    public static String of(SecurityIdentity identity) {
        if (identity == null || identity.isAnonymous() || !(identity.getPrincipal() instanceof JsonWebToken jwt)) {
            return null;
        }
        String email = jwt.getClaim("email");
        if (email == null || email.isBlank() || !isTrue(jwt.getClaim("email_verified"))) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // Cognito sends email_verified as a string ("true"), Keycloak as a boolean.
    private static boolean isTrue(Object claim) {
        if (claim instanceof Boolean b) {
            return b;
        }
        if (claim instanceof jakarta.json.JsonValue value) {
            return value == jakarta.json.JsonValue.TRUE
                    || (value instanceof jakarta.json.JsonString s && "true".equalsIgnoreCase(s.getString()));
        }
        return claim != null && "true".equalsIgnoreCase(claim.toString());
    }
}

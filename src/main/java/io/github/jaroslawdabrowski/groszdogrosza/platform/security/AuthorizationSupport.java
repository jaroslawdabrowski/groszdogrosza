package io.github.jaroslawdabrowski.groszdogrosza.platform.security;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.ParentRole;
import io.github.jaroslawdabrowski.groszdogrosza.parent.port.in.GetParentByEmailUseCase;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import java.util.Optional;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Every {@code /api/*} endpoint requires an authenticated Cognito/Keycloak user via
 * {@code @Authenticated}, but that alone doesn't distinguish the treasurer (who manages
 * every parent's money) from a regular parent (who must only ever see and touch their own
 * data). Who's the treasurer is stored on {@link Parent#role()} - this app's own data - NOT
 * derived from an identity-provider role/group claim: that keeps a single source of truth
 * instead of needing matching Keycloak-realm-role/Cognito-group configuration kept in sync
 * across two environments, and lets the treasurer manage it the same way they manage
 * everything else (create a Parent record with role=TREASURER).
 *
 * <p>A parent's own identity is established by matching the OIDC token's verified
 * {@code email} claim against {@code Parent.email} - no separate account-linking step or
 * stored subject id is needed, since the treasurer already enters each parent's real email
 * when creating their record and Cognito only issues accounts the treasurer created for that
 * same address.
 */
@ApplicationScoped
public class AuthorizationSupport {

    @Inject
    GetParentByEmailUseCase getParentByEmailUseCase;

    /** Null if the current principal isn't a JWT or carries no {@code email} claim. */
    public String currentUserEmail(SecurityIdentity identity) {
        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            return jwt.getClaim("email");
        }
        return null;
    }

    public Optional<Parent> currentParent(SecurityIdentity identity) {
        String email = currentUserEmail(identity);
        return email == null ? Optional.empty() : getParentByEmailUseCase.getParentByEmail(email);
    }

    public boolean isTreasurer(SecurityIdentity identity) {
        return currentParent(identity).map(parent -> parent.role() == ParentRole.TREASURER).orElse(false);
    }

    public void requireTreasurer(SecurityIdentity identity) {
        if (!isTreasurer(identity)) {
            throw new ForbiddenException("This action is restricted to the treasurer");
        }
    }

    /** @param parentEmail the email of the parent record being accessed */
    public void requireSelfOrTreasurer(SecurityIdentity identity, String parentEmail) {
        if (isTreasurer(identity)) {
            return;
        }
        String callerEmail = currentUserEmail(identity);
        if (callerEmail == null || parentEmail == null || !callerEmail.equalsIgnoreCase(parentEmail)) {
            throw new ForbiddenException("Not authorized to access this parent's data");
        }
    }

    /**
     * Same self-or-treasurer rule as {@link #requireSelfOrTreasurer}, but for a student's
     * piggy bank/ledger - "self" here means the caller's own {@code Parent} record is linked
     * to this student ({@code Parent.studentId}), not that the caller *is* the student
     * (students never log in at all).
     */
    public void requireSelfOrTreasurerForStudent(SecurityIdentity identity, String studentId) {
        if (isTreasurer(identity)) {
            return;
        }
        boolean isOwnStudent = currentParent(identity).map(parent -> studentId.equals(parent.studentId())).orElse(false);
        if (!isOwnStudent) {
            throw new ForbiddenException("Not authorized to access this student's data");
        }
    }
}

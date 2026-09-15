package io.github.jaroslawdabrowski.groszdogrosza.parent.domain;

/**
 * Who this Parent account belongs to, for authorization - see
 * {@code platform.security.AuthorizationSupport}. Deliberately stored on the Parent record
 * itself (the app's own data, matched against the OIDC token's {@code email} claim) rather
 * than derived from an identity-provider role/group claim: it keeps the single source of
 * truth for "who's the treasurer" in this app's own database instead of needing matching
 * Keycloak-realm-role/Cognito-group configuration kept in sync across two environments.
 */
public enum ParentRole {
    /** The treasurer - manages every collection and every parent's money. Normally exactly
     * one in this app (see CLAUDE.md, "single class" scope), but nothing enforces that. */
    TREASURER,
    /** A regular parent - only ever sees and touches their own data. */
    PARENT
}

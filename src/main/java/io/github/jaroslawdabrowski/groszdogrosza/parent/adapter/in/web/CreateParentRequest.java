package io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web;

/** @param role "TREASURER" or "PARENT" (case-sensitive, matches {@code ParentRole}); null defaults to PARENT. */
public record CreateParentRequest(String firstName, String lastName, String email, String expectedSenderName,
        String role) {
}

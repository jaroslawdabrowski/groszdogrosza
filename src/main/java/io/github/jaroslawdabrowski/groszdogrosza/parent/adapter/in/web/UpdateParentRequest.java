package io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web;

public record UpdateParentRequest(String firstName, String lastName, String email, String expectedSenderName) {
}

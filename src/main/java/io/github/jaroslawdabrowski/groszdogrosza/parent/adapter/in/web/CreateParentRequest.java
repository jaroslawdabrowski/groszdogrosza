package io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web;

/** A new parent of a student. There's no role here: being a treasurer is a separate,
 *  per-class membership (schoolclass), never something set on a parent record. */
public record CreateParentRequest(String firstName, String lastName, String email, String expectedSenderName) {
}

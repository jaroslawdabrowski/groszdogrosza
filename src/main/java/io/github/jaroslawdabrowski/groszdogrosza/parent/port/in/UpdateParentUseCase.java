package io.github.jaroslawdabrowski.groszdogrosza.parent.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;

/** Treasurer-only: edit a parent's basic profile fields (name, email, expected sender name)
 *  - e.g. filling in an email that was left blank, or fixing a typo. Deliberately does NOT
 *  touch {@code studentId}, {@code role}, or {@code paymentInfo} - those already have their
 *  own dedicated, more carefully-guarded use cases. */
public interface UpdateParentUseCase {

    Parent updateParent(String parentId, String firstName, String lastName, String email, String expectedSenderName);
}

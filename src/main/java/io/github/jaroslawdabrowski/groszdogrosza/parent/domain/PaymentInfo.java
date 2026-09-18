package io.github.jaroslawdabrowski.groszdogrosza.parent.domain;

/**
 * How to send money to whichever {@link Parent} this belongs to - in practice only ever set
 * on the treasurer's own record, since every collection is paid into the treasurer's single
 * account regardless of which parent is paying (see CLAUDE.md, "Public collection overview").
 * Both fields are nullable/settable independently (e.g. a parent might only ever use BLIK).
 */
public record PaymentInfo(String bankAccountNumber, String blikPhoneNumber) {
}

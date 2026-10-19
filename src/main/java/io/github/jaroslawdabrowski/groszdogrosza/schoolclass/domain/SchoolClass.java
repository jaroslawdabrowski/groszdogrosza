package io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain;

import java.time.Instant;

/**
 * One school class (e.g. "SP 34 - 2A") - the boundary of everything money-related: its
 * students, collections, piggy banks and log never mix with another class's. See
 * docs/MULTI-CLASS.md.
 *
 * @param publicToken the secret part of the class's public link ({@code /k/<token>}) - shows
 *                    totals and payment details without logging in. Long and random so it
 *                    can't be guessed or enumerated; the treasurer can replace it if it leaks.
 * @param paymentInfo where parents send money for this class's collections (one account for
 *                    every collection of the class) - null until the treasurer fills it in
 */
public record SchoolClass(String id, String name, String publicToken, PaymentInfo paymentInfo, Instant createdAt) {

    public SchoolClass withPaymentInfo(PaymentInfo newPaymentInfo) {
        return new SchoolClass(id, name, publicToken, newPaymentInfo, createdAt);
    }
}

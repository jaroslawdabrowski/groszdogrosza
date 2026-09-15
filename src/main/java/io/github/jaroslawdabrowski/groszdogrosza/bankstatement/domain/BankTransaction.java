package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One incoming transfer parsed out of an mBank statement HTML attachment.
 *
 * @param senderName    exactly as printed on the statement (e.g. "JAN KOWALSKI" or
 *                      "Kowalski Jan") - {@code ParentMatchingPolicy} is responsible for
 *                      normalizing this before comparing it to a parent's expected name.
 * @param bankReference mBank's own transaction id if the parsed HTML exposes one;
 *                      otherwise a deterministic hash of (senderName, title, amount,
 *                      transactionDate) computed by the parser, used as the idempotency
 *                      key so the same transaction is never booked twice even across
 *                      overlapping poll windows.
 */
public record BankTransaction(
        String senderName,
        String title,
        BigDecimal amount,
        LocalDate transactionDate,
        String bankReference) {
}

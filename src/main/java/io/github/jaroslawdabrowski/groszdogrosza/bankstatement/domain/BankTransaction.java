package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One incoming transfer parsed out of an mBank "Powiadomienie e-mail" notification.
 *
 * @param senderName    exactly as printed in the notification (e.g. "Jan Kowalski") -
 *                      {@code ParentMatchingPolicy} is responsible for normalizing this
 *                      before comparing it to a parent's expected name.
 * @param title         the trailing reference code from the notification sentence (e.g.
 *                      {@code "/OPF/AN/PL11..."}) - NOT confirmed to be a human-typed
 *                      transfer title (it may be a structured payment reference or a masked
 *                      source IBAN instead); see {@code MBankStatementHtmlParser}'s javadoc.
 * @param bankReference a hash of the transaction's date plus its FULL notification sentence
 *                      (not just sender/title/amount/date, since mBank's notifications don't
 *                      expose a separate transaction id) - the sentence includes the running
 *                      account balance after the operation, which differs between any two
 *                      real operations even if sender/amount/date otherwise match, so this
 *                      is the idempotency key that ensures the same transaction is never
 *                      booked twice even across overlapping poll windows.
 */
public record BankTransaction(
        String senderName,
        String title,
        BigDecimal amount,
        LocalDate transactionDate,
        String bankReference) {
}

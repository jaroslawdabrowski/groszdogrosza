package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.out;

import java.time.Instant;
import java.util.List;

/**
 * Fetches raw mBank statement attachments received by email since the last processed
 * point in time. Implemented by {@code adapter.out.mail.imap.ImapBankStatementFetchAdapter}.
 */
public interface BankStatementFetchPort {

    List<RawStatementAttachment> fetchNewStatementsSince(Instant since);

    record RawStatementAttachment(String messageId, Instant receivedAt, String htmlContent) {
    }
}

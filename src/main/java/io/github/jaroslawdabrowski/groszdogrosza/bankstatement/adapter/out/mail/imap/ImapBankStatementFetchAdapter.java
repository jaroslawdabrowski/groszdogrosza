package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.adapter.out.mail.imap;

import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.out.BankStatementFetchPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Reads the daily mBank statement mail via plain IMAP with a Gmail App Password, rather
 * than the full Gmail API + OAuth consent flow. See CLAUDE.md ("Why IMAP instead of the
 * Gmail API") for the reasoning - short version: this is a single-user, single-mailbox,
 * personal-Gmail use case where a scoped App Password (revocable, no broad account access)
 * is simpler to operate than registering a Google Cloud OAuth client and running a consent
 * flow for an unattended Lambda.
 *
 * <p>Credentials come from config (groszdogrosza.bankstatement.imap.*), deliberately left
 * empty in the repo - see application.properties. Never commit a real App Password.
 */
@ApplicationScoped
public class ImapBankStatementFetchAdapter implements BankStatementFetchPort {

    private static final Logger LOG = Logger.getLogger(ImapBankStatementFetchAdapter.class);

    @ConfigProperty(name = "groszdogrosza.bankstatement.imap.host")
    String host;

    @ConfigProperty(name = "groszdogrosza.bankstatement.imap.port")
    int port;

    @ConfigProperty(name = "groszdogrosza.bankstatement.imap.username")
    String username;

    @ConfigProperty(name = "groszdogrosza.bankstatement.imap.app-password")
    String appPassword;

    @ConfigProperty(name = "groszdogrosza.bankstatement.imap.mailbox")
    String mailbox;

    @ConfigProperty(name = "groszdogrosza.bankstatement.imap.expected-sender")
    String expectedSender;

    @Override
    public List<RawStatementAttachment> fetchNewStatementsSince(Instant since) {
        if (username == null || username.isBlank() || appPassword == null || appPassword.isBlank()) {
            LOG.warn("IMAP credentials not configured (groszdogrosza.bankstatement.imap.username/app-password) - "
                    + "skipping bank statement fetch. This is expected until Gmail App Password setup is done "
                    + "(see CLAUDE.md TODOs).");
            return List.of();
        }

        List<RawStatementAttachment> attachments = new ArrayList<>();
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", String.valueOf(port));
        props.put("mail.imaps.ssl.enable", "true");

        Session session = Session.getInstance(props);
        try (Store store = session.getStore("imaps")) {
            store.connect(host, port, username, appPassword);
            Folder inbox = store.getFolder(mailbox);
            inbox.open(Folder.READ_ONLY);

            SearchTerm searchTerm = new AndTerm(
                    new FromStringTerm(expectedSender),
                    new ReceivedDateTerm(ReceivedDateTerm.GE, Date.from(since)));
            Message[] messages = inbox.search(searchTerm);

            for (Message message : messages) {
                java.util.Optional<String> html = extractHtmlPart(message);
                if (html.isPresent()) {
                    attachments.add(new RawStatementAttachment(
                            messageIdOf(message), message.getReceivedDate().toInstant(), html.get()));
                }
            }

            inbox.close(false);
        } catch (MessagingException e) {
            // Deliberately swallow-and-log rather than propagate: a single failed poll
            // (transient network issue, Gmail rate limiting, etc.) should not crash the
            // scheduled/EventBridge-triggered invocation - the next poll will retry, and
            // nothing here has side effects yet (fetching is read-only).
            LOG.error("Failed to fetch bank statement mail over IMAP", e);
        }

        return attachments;
    }

    /**
     * A real mBank "Powiadomienie e-mail" was confirmed to be a plain
     * {@code text/html} document (see {@code MBankStatementHtmlParser}'s javadoc and the
     * anonymized fixture) - as delivered by Gmail this is very likely wrapped in
     * {@code multipart/alternative} (a plain-text sibling part) and possibly further inside
     * {@code multipart/related} (e.g. an embedded bank logo image), so this walks the whole
     * MIME tree recursively for the first {@code text/html} part instead of assuming a
     * fixed one-level structure.
     */
    private static java.util.Optional<String> extractHtmlPart(Part part) {
        try {
            if (part.isMimeType("text/html")) {
                return java.util.Optional.of((String) part.getContent());
            }
            if (part.isMimeType("multipart/*") && part.getContent() instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    java.util.Optional<String> found = extractHtmlPart(multipart.getBodyPart(i));
                    if (found.isPresent()) {
                        return found;
                    }
                }
            }
        } catch (MessagingException | IOException e) {
            LOG.error("Failed to extract HTML content from bank statement mail", e);
        }
        return java.util.Optional.empty();
    }

    private static String messageIdOf(Message message) {
        try {
            String[] headers = message.getHeader("Message-ID");
            if (headers != null && headers.length > 0) {
                return headers[0];
            }
        } catch (MessagingException e) {
            LOG.warn("Failed to read Message-ID header, falling back to a synthetic id", e);
        }
        return "unknown-" + Instant.now().atZone(ZoneId.systemDefault());
    }
}

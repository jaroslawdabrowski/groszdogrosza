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
import java.util.Optional;
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
 *
 * <p>{@code username}/{@code appPassword} are {@code Optional<String>}, not plain
 * {@code String} - SmallRye Config treats a property with an empty value (as opposed to one
 * that's absent entirely) as "not set" by default, so a required (non-Optional)
 * {@code @ConfigProperty String} with no default value fails Quarkus startup outright when
 * the property is present-but-empty, exactly the deliberately-blank-until-configured state
 * this field is in. This is NOT hypothetical - it broke `quarkus:dev` entirely the first
 * time this was actually run locally.
 */
@ApplicationScoped
public class ImapBankStatementFetchAdapter implements BankStatementFetchPort {

    private static final Logger LOG = Logger.getLogger(ImapBankStatementFetchAdapter.class);

    @ConfigProperty(name = "groszdogrosza.bankstatement.imap.host")
    String host;

    @ConfigProperty(name = "groszdogrosza.bankstatement.imap.port")
    int port;

    @ConfigProperty(name = "groszdogrosza.bankstatement.imap.username")
    Optional<String> username;

    @ConfigProperty(name = "groszdogrosza.bankstatement.imap.app-password")
    Optional<String> appPassword;

    @ConfigProperty(name = "groszdogrosza.bankstatement.imap.mailbox")
    String mailbox;

    @ConfigProperty(name = "groszdogrosza.bankstatement.imap.expected-sender")
    String expectedSender;

    @Override
    public List<RawStatementAttachment> fetchNewStatementsSince(Instant since) {
        if (username.isEmpty() || username.get().isBlank() || appPassword.isEmpty() || appPassword.get().isBlank()) {
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
            store.connect(host, port, username.get(), appPassword.get());
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
     * Confirmed against a real raw {@code .eml}: the message is S/MIME-signed
     * ({@code multipart/signed}, one part being the actual message, the other a detached
     * {@code application/pkcs7-signature} we don't care about), and the actual message is
     * {@code multipart/mixed} with TWO {@code text/html} parts, not one - a generic "Dzień
     * dobry, przesyłamy dzienny wykaz..." greeting nested inside a {@code multipart/alternative}
     * (paired with a {@code text/plain} sibling), and, as a SEPARATE sibling part carrying
     * {@code Content-Disposition: attachment; filename="Powiadomienie e-mail z YYYY-MM-DD.htm"},
     * the actual notification table this parser needs. A naive "first text/html part found"
     * walk would silently grab the greeting (wrong - no operations table in it, so the
     * statement parser would find nothing and every transaction for that day would be
     * missed) since the alternative part comes first in MIME order. This does two passes:
     * first only accepting a {@code text/html} part whose {@code Content-Disposition} is
     * {@code attachment} (matches the real structure), falling back to any {@code text/html}
     * part only if no attachment-disposition one exists at all (defensive, in case mBank
     * ever changes this).
     */
    static java.util.Optional<String> extractHtmlPart(Part part) {
        java.util.Optional<String> attachment = findHtmlPart(part, true);
        return attachment.isPresent() ? attachment : findHtmlPart(part, false);
    }

    private static java.util.Optional<String> findHtmlPart(Part part, boolean requireAttachmentDisposition) {
        try {
            if (part.isMimeType("text/html")) {
                boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition());
                if (!requireAttachmentDisposition || isAttachment) {
                    return java.util.Optional.of((String) part.getContent());
                }
                return java.util.Optional.empty();
            }
            if (part.isMimeType("multipart/*") && part.getContent() instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    java.util.Optional<String> found = findHtmlPart(multipart.getBodyPart(i), requireAttachmentDisposition);
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

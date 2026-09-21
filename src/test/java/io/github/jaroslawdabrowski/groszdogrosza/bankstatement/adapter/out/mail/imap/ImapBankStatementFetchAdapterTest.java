package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.adapter.out.mail.imap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.activation.DataHandler;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Regression test for a real bug found against an actual mBank notification's raw
 * {@code .eml}: the message contains TWO {@code text/html} parts (a generic greeting nested
 * in a {@code multipart/alternative}, which comes first in MIME order, and the real
 * notification table as a separate {@code Content-Disposition: attachment} sibling part) -
 * a naive "first text/html part found" walk silently picks the wrong one, so every
 * transaction for that day would have gone unbooked with no error at all. See
 * {@code ImapBankStatementFetchAdapter.extractHtmlPart}'s javadoc for the full MIME shape.
 */
class ImapBankStatementFetchAdapterTest {

    @Test
    void picksTheAttachmentHtmlPartNotTheAlternativeGreetingPart() throws MessagingException, java.io.IOException {
        Part message = realisticMBankMimeStructure(
                "<html><body>Dzien dobry, przesylamy dzienny wykaz operacji.</body></html>",
                "<html><body><h1 class=\"h1\">2026-09-15 - Powiadomienie e-mail</h1>"
                        + "<table><tr><th class=\"th\">Opis operacji</th></tr></table></body></html>");

        Optional<String> extracted = ImapBankStatementFetchAdapter.extractHtmlPart(message);

        assertTrue(extracted.isPresent());
        assertTrue(extracted.get().contains("Opis operacji"), "must pick the attachment part with the real table, "
                + "not the alternative-part greeting");
    }

    /**
     * Regression test for a real production bug (2026-09-21): a genuine mBank notification's
     * attachment part declares {@code Content-Type: text/html; name="..."} with NO charset
     * parameter at all - only the HTML's own {@code <meta charset=iso-8859-2>} tag says so,
     * which jakarta.mail never looks at. The original code used {@code part.getContent()},
     * which defaults an undeclared charset to something that mangles Polish diacritics; one
     * surname ("Woś") came through corrupted enough that {@code PaymentMatchingPolicy}'s
     * exact-word surname match silently failed for that specific transaction, while others
     * happened to survive by coincidence - see ImapBankStatementFetchAdapter's own javadoc for
     * the full explanation. This proves the fix (explicit ISO-8859-2 fallback, not whatever
     * jakarta.mail's own default happens to be) actually round-trips the real character.
     */
    @Test
    void decodesTheAttachmentPartAsIso88592WhenItDeclaresNoCharsetAtAll() throws MessagingException, java.io.IOException {
        String statementHtml = "<html><body><h1 class=\"h1\">2026-09-20 - Powiadomienie e-mail</h1>"
                + "<table><tr><th class=\"th\">Opis operacji</th></tr>"
                + "<tr><td class=\"td\">czas</td><td class=\"td\">od WOŚ ALEKSANDRA WERONIKA</td></tr>"
                + "</table></body></html>";
        byte[] iso88592Bytes = statementHtml.getBytes(Charset.forName("ISO-8859-2"));

        MimeBodyPart attachmentPart = new MimeBodyPart();
        // No charset parameter here - exactly what the real mBank attachment part sends.
        String contentType = "text/html; name=\"Powiadomienie e-mail z 2026-09-20.htm\"";
        attachmentPart.setDataHandler(new DataHandler(new ByteArrayDataSource(iso88592Bytes, contentType)));
        attachmentPart.setHeader("Content-Type", contentType);
        attachmentPart.setDisposition(Part.ATTACHMENT);
        attachmentPart.setFileName("Powiadomienie e-mail z 2026-09-20.htm");

        Optional<String> extracted = ImapBankStatementFetchAdapter.extractHtmlPart(attachmentPart);

        assertTrue(extracted.isPresent());
        assertTrue(extracted.get().contains("WOŚ ALEKSANDRA WERONIKA"),
                "expected the real Polish surname to decode correctly, got: " + extracted.get());
    }

    /**
     * When a part DOES declare its own charset, that declaration must win over the
     * ISO-8859-2 fallback - the fallback exists specifically for the undeclared case above,
     * not as a blanket override of whatever a part actually says about itself.
     */
    @Test
    void honorsAnExplicitlyDeclaredCharsetInsteadOfForcingTheFallback() throws MessagingException, java.io.IOException {
        String html = "<html><body>UTF-8 sender: Woś Aleksandra</body></html>";
        byte[] utf8Bytes = html.getBytes(StandardCharsets.UTF_8);

        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDataHandler(new DataHandler(new ByteArrayDataSource(utf8Bytes, "text/html; charset=UTF-8")));
        attachmentPart.setHeader("Content-Type", "text/html; charset=UTF-8");
        attachmentPart.setDisposition(Part.ATTACHMENT);
        attachmentPart.setFileName("statement.htm");

        Optional<String> extracted = ImapBankStatementFetchAdapter.extractHtmlPart(attachmentPart);

        assertTrue(extracted.isPresent());
        assertTrue(extracted.get().contains("Woś Aleksandra"), "got: " + extracted.get());
    }

    @Test
    void fallsBackToAnyHtmlPartWhenNoneIsMarkedAsAnAttachment() throws MessagingException {
        MimeMultipart alternative = new MimeMultipart("alternative");
        alternative.addBodyPart(textPart("text/plain", "plain text"));
        alternative.addBodyPart(inlineHtmlPart("<html><body>only html, no attachment disposition</body></html>"));

        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setContent(alternative);
        message.saveChanges();

        Optional<String> extracted = ImapBankStatementFetchAdapter.extractHtmlPart(message);

        assertTrue(extracted.isPresent());
        assertEquals("<html><body>only html, no attachment disposition</body></html>", extracted.get());
    }

    /** Mirrors the real structure: multipart/signed(multipart/mixed(multipart/alternative(text/plain, text/html), text/html-attachment), pkcs7-signature). */
    private static MimeMessage realisticMBankMimeStructure(String greetingHtml, String statementHtml)
            throws MessagingException, java.io.IOException {
        MimeMultipart alternative = new MimeMultipart("alternative");
        alternative.addBodyPart(textPart("text/plain", "Dzien dobry"));
        alternative.addBodyPart(inlineHtmlPart(greetingHtml));

        MimeBodyPart alternativePart = new MimeBodyPart();
        alternativePart.setContent(alternative);

        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDataHandler(new DataHandler(
                new ByteArrayDataSource(statementHtml, "text/html; name=\"Powiadomienie e-mail z 2026-09-15.htm\"")));
        attachmentPart.setDisposition(Part.ATTACHMENT);
        attachmentPart.setFileName("Powiadomienie e-mail z 2026-09-15.htm");

        MimeMultipart mixed = new MimeMultipart("mixed");
        mixed.addBodyPart(alternativePart);
        mixed.addBodyPart(attachmentPart);

        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setContent(mixed);
        message.saveChanges();
        return message;
    }

    private static BodyPart textPart(String mimeType, String content) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart();
        part.setContent(content, mimeType);
        return part;
    }

    private static BodyPart inlineHtmlPart(String html) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart();
        part.setContent(html, "text/html");
        return part;
    }
}

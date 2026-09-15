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

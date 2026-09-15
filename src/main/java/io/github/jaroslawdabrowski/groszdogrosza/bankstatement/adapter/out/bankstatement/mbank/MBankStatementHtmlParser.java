package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.adapter.out.bankstatement.mbank;

import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain.BankTransaction;
import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.out.StatementParserPort;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Parses mBank's "Powiadomienie e-mail" (daily account-activity notification) HTML into
 * {@link BankTransaction}s - confirmed against a real sample email (redacted before being
 * committed; see {@code src/test/resources/mbank/sample-statement.html}'s header comment).
 *
 * <p>This is NOT a batch statement export with one structured row per transaction field
 * (date/sender/title/amount columns), which is what the original, unverified version of
 * this parser guessed. The real shape is an event log: a single two-column table
 * ("Czas operacji" / "Opis operacji") whose second column is one free-text Polish sentence
 * per account event that day, mixing genuine incoming transfers with unrelated events (a
 * login confirmation was the only other kind seen in the sample; outgoing transfers, card
 * payments etc. presumably also appear this way but haven't been observed yet). There is no
 * separate date column - the notification covers exactly one calendar day, printed once in
 * the page heading ("YYYY-MM-DD - Powiadomienie e-mail"), and each row only has a time.
 *
 * <p>Only rows matching {@link #INCOMING_TRANSFER_PATTERN} become a {@link BankTransaction}
 * - every other row (a login, or any sentence not shaped like this exact incoming-transfer
 * pattern) is silently skipped. This is also what keeps an outgoing transaction from ever
 * being mistaken for an incoming contribution - unlike the original guessed parser, there is
 * no minus-sign/direction field to get wrong, because only the recognized incoming-transfer
 * sentence shape is ever converted to a transaction at all.
 */
@ApplicationScoped
public class MBankStatementHtmlParser implements StatementParserPort {

    private static final Logger LOG = Logger.getLogger(MBankStatementHtmlParser.class);

    /**
     * Matches e.g. {@code "mBank: Przelew przych. z rach. 1111...222222 na rach. 33334444
     * kwota 50,00 PLN od Anna Nowak .; /OPF/AN/PL11...; Dost. 1200,00 PLN"} (see the
     * anonymized fixture, {@code src/test/resources/mbank/sample-statement.html}).
     * Group 1 = amount ("50,00"), group 2 = sender name, group 3 = the trailing reference
     * code (e.g. "/OPF/AN/PL11...") - its exact meaning (a structured payment reference? a
     * masked source IBAN?) is NOT confirmed; treat it as an opaque reference, not
     * necessarily a human-typed transfer title, until a sample with a real free-text title
     * is observed.
     */
    private static final Pattern INCOMING_TRANSFER_PATTERN = Pattern.compile(
            "Przelew przych\\..*?kwota\\s+([0-9][0-9 ]*,[0-9]{2})\\s*PLN\\s+od\\s+(.+?)\\s*\\.;\\s*(.*?);\\s*Dost\\.",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EMAIL_DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    @Override
    public List<BankTransaction> parse(String htmlContent) {
        Document document = Jsoup.parse(htmlContent);
        LocalDate emailDate = parseEmailDate(document);

        Elements operationRows = operationRows(document);
        if (operationRows.isEmpty()) {
            LOG.warn("mBank notification parser found no 'Opis operacji' table - the HTML structure doesn't "
                    + "match the confirmed sample (see MBankStatementHtmlParser's class javadoc and "
                    + "src/test/resources/mbank/sample-statement.html). No transactions extracted from this mail.");
            return List.of();
        }

        List<BankTransaction> transactions = new ArrayList<>();
        for (Element row : operationRows) {
            Elements cells = row.select("td");
            if (cells.size() < 2) {
                continue;
            }
            String description = cells.get(1).text();
            Matcher matcher = INCOMING_TRANSFER_PATTERN.matcher(description);
            if (!matcher.find()) {
                // Not an incoming transfer (a login confirmation, presumably an outgoing
                // payment or card transaction too, ...) - deliberately not booked, see the
                // class javadoc for why this is also the direction-safety mechanism.
                continue;
            }
            try {
                transactions.add(toTransaction(matcher, description, emailDate));
            } catch (RuntimeException e) {
                LOG.errorf(e, "Failed to parse a matched mBank operation row - skipping it: %s", description);
            }
        }
        return transactions;
    }

    private static Elements operationRows(Document document) {
        Element operationsTable = document.select("table:has(th:matchesOwn((?i)Opis operacji))").first();
        if (operationsTable == null) {
            return new Elements();
        }
        Elements dataRows = new Elements();
        for (Element row : operationsTable.select("tr")) {
            if (row.select("th").isEmpty()) {
                dataRows.add(row);
            }
        }
        return dataRows;
    }

    private static BankTransaction toTransaction(Matcher matcher, String fullDescription, LocalDate emailDate) {
        BigDecimal amount = parseAmount(matcher.group(1));
        String senderName = matcher.group(2).trim();
        String reference = matcher.group(3).trim();
        String bankReference = computeReferenceHash(emailDate, fullDescription);
        return new BankTransaction(senderName, reference, amount, emailDate, bankReference);
    }

    private static BigDecimal parseAmount(String raw) {
        // Polish decimal comma, e.g. "5,00" or "1 250,00" (thousands separated by a space).
        String cleaned = raw.replace(" ", "").replace(",", ".");
        return new BigDecimal(cleaned);
    }

    private static LocalDate parseEmailDate(Document document) {
        String headingText = document.select(".h1").text();
        Matcher matcher = EMAIL_DATE_PATTERN.matcher(headingText);
        if (matcher.find()) {
            return LocalDate.parse(matcher.group(1));
        }
        throw new IllegalStateException(
                "Could not find the notification's date in its heading (expected 'YYYY-MM-DD - ...', got: '"
                        + headingText + "')");
    }

    /**
     * mBank's "Opis operacji" sentence has no separate transaction id, but it does include
     * the account's running available balance after the operation ("Dost. X PLN"), which
     * necessarily differs between any two real, distinct operations (even two transfers of
     * the same amount from the same sender on the same day have different post-balances) -
     * so hashing the FULL row text (not just amount/sender/date, as the original guessed
     * parser did) is a meaningfully more specific idempotency key, deliberately chosen to
     * avoid the hash-collision risk two truly-identical-looking transfers would otherwise
     * have.
     */
    private static String computeReferenceHash(LocalDate emailDate, String fullDescription) {
        String raw = emailDate + "|" + fullDescription;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JVM - this is unreachable in practice.
            throw new IllegalStateException(e);
        }
    }
}

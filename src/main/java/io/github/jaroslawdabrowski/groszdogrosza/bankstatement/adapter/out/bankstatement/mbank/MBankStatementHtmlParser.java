package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.adapter.out.bankstatement.mbank;

import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain.BankTransaction;
import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.out.StatementParserPort;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.jboss.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Parses mBank's daily account statement HTML email attachment into {@link BankTransaction}s.
 *
 * <p><b>THIS IS A BEST-EFFORT SKELETON, NOT A VERIFIED PARSER.</b> It was written without
 * a real sample of the mBank statement HTML to check selectors against - the table/row/cell
 * structure below (a table with one {@code <tr>} per transaction, columns for date/sender-
 * or-recipient/title/amount) is a reasonable guess at what a bank statement export
 * typically looks like, not a confirmed one. Before relying on this for real automatic
 * bookkeeping:
 * <ol>
 *   <li>Get a real mBank statement HTML attachment (forward yourself a day's statement,
 *       save the attachment).</li>
 *   <li>Save it as {@code src/test/resources/mbank/sample-statement.html} (gitignored
 *       pattern already covers *.local if it contains real account data - check before
 *       committing a real sample, redact account numbers/balances if needed).</li>
 *   <li>Update the CSS selectors in {@link #parse(String)} below to match its actual
 *       structure, and un-skip {@code MBankStatementHtmlParserTest}.</li>
 * </ol>
 */
@ApplicationScoped
public class MBankStatementHtmlParser implements StatementParserPort {

    private static final Logger LOG = Logger.getLogger(MBankStatementHtmlParser.class);

    // TODO: confirm against a real sample - guessed selectors for a transactions table.
    private static final String TRANSACTION_ROW_SELECTOR = "table.transactions tr.transaction-row";
    private static final String SENDER_CELL_SELECTOR = ".sender-name";
    private static final String TITLE_CELL_SELECTOR = ".transaction-title";
    private static final String AMOUNT_CELL_SELECTOR = ".amount";
    private static final String DATE_CELL_SELECTOR = ".transaction-date";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Override
    public List<BankTransaction> parse(String htmlContent) {
        List<BankTransaction> transactions = new ArrayList<>();
        Document document = Jsoup.parse(htmlContent);
        Elements rows = document.select(TRANSACTION_ROW_SELECTOR);

        if (rows.isEmpty()) {
            LOG.warnf("mBank statement parser found zero transaction rows using selector '%s' - "
                    + "the HTML structure likely doesn't match what this parser expects yet "
                    + "(see class javadoc: it was written without a real sample). No transactions "
                    + "extracted from this attachment.", TRANSACTION_ROW_SELECTOR);
            return transactions;
        }

        for (Element row : rows) {
            try {
                transactions.add(parseRow(row));
            } catch (RuntimeException e) {
                LOG.errorf(e, "Failed to parse a transaction row from the mBank statement - skipping it: %s",
                        row.text());
            }
        }
        return transactions;
    }

    private BankTransaction parseRow(Element row) {
        String senderName = textOf(row, SENDER_CELL_SELECTOR);
        String title = textOf(row, TITLE_CELL_SELECTOR);
        BigDecimal amount = parseAmount(textOf(row, AMOUNT_CELL_SELECTOR));
        LocalDate transactionDate = parseDate(textOf(row, DATE_CELL_SELECTOR));
        String bankReference = computeReferenceHash(senderName, title, amount, transactionDate);

        return new BankTransaction(senderName, title, amount, transactionDate, bankReference);
    }

    private static String textOf(Element row, String selector) {
        Element cell = row.selectFirst(selector);
        if (cell == null) {
            throw new IllegalStateException("Expected cell '" + selector + "' not found in row");
        }
        return cell.text().trim();
    }

    private static BigDecimal parseAmount(String raw) {
        // mBank statements typically render amounts like "+150,00 PLN" or "-45,50 PLN" -
        // normalize the Polish decimal comma and strip the currency suffix/sign markers.
        // TODO: confirm exact formatting (thousands separator, always-signed?) against a
        // real sample.
        String cleaned = raw.replace("PLN", "")
                .replace("zł", "")
                .replace(" ", "")
                .replace(",", ".")
                .replace("+", "")
                .trim();
        return new BigDecimal(cleaned).abs();
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("Could not parse transaction date '" + raw + "'", e);
        }
    }

    /**
     * mBank's own transaction reference isn't assumed to be present in the HTML export (it
     * may or may not be, unconfirmed) - a stable hash of the transaction's own fields is
     * used as the idempotency key instead, so the same statement parsed twice (e.g.
     * overlapping poll windows) always produces the same {@code bankReference}.
     */
    private static String computeReferenceHash(String senderName, String title, BigDecimal amount, LocalDate date) {
        String raw = String.join("|", senderName, title, amount.toPlainString(), date.toString());
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

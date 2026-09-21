package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.adapter.out.bankstatement.mbank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain.BankTransaction;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises the parser against {@code src/test/resources/mbank/sample-statement.html}, an
 * anonymized copy of a real mBank "Powiadomienie e-mail" notification (see that file's
 * header comment) - unlike the original placeholder fixture, this proves the parser against
 * the real markup shape mBank actually sends, not a guessed one.
 */
class MBankStatementHtmlParserTest {

    private final MBankStatementHtmlParser parser = new MBankStatementHtmlParser();

    @Test
    void parsesIncomingTransfersAndSkipsNonTransferEvents() throws IOException {
        List<BankTransaction> transactions = parser.parse(readFixture());

        // The fixture has 3 operations: two incoming transfers and one login confirmation -
        // only the transfers should turn into a BankTransaction.
        assertEquals(2, transactions.size());

        BankTransaction first = transactions.get(0);
        assertEquals("Anna Nowak", first.senderName());
        assertEquals(new BigDecimal("50.00"), first.amount());
        assertEquals(LocalDate.of(2026, 9, 15), first.transactionDate());

        BankTransaction second = transactions.get(1);
        assertEquals("Piotr Wisniewski", second.senderName());
        assertEquals(new BigDecimal("45.50"), second.amount());
    }

    /**
     * A real notification (2026-09-20/21) had two rows out of seven where mBank omitted the
     * usual " ." after the sender name (going straight to ";") - the original pattern
     * required that literal dot, so both rows silently failed to match at all and the
     * transfers were never booked (confirmed against production: {@code transactionsSeen=5}
     * for a mail with 7 real transfers). See INCOMING_TRANSFER_PATTERN's javadoc for the
     * exact failure mechanism this fixture reproduces (row 1 has the dot, rows 2 and 3 don't).
     */
    @Test
    void parsesTransfersEvenWhenMbankOmitsTheDotAfterTheSenderName() throws IOException {
        List<BankTransaction> transactions = parser.parse(readFixture("missing-sender-dot-statement.html"));

        assertEquals(3, transactions.size());

        BankTransaction dotted = transactions.get(0);
        assertEquals("Jakub Testowy", dotted.senderName());
        assertEquals(new BigDecimal("150.00"), dotted.amount());

        BankTransaction noDotEllipsisTitle = transactions.get(1);
        assertEquals("DAWID PRZYKLADOWY", noDotEllipsisTitle.senderName());
        assertEquals(new BigDecimal("100.00"), noDotEllipsisTitle.amount());

        BankTransaction noDotSimpleTitle = transactions.get(2);
        assertEquals("ANNA WZOROWA", noDotSimpleTitle.senderName());
        assertEquals(new BigDecimal("100.00"), noDotSimpleTitle.amount());
    }

    @Test
    void referenceHashesAreUniquePerOperationEvenWithSameSenderAndAmount() throws IOException {
        List<BankTransaction> transactions = parser.parse(readFixture());

        assertNotEquals(transactions.get(0).bankReference(), transactions.get(1).bankReference());
    }

    @Test
    void twoOtherwiseIdenticalTransfersStillGetDifferentReferencesBecauseThePostBalanceDiffers() {
        String html = notificationHtml(
                "2026-09-16",
                "<tr><td class=\"td\"><nobr>10:00</nobr></td><td class=\"td\">mBank: Przelew przych. z rach. "
                        + "1111...222222 na rach. 33334444 kwota 50,00 PLN od Jan Testowy .; /OPF/AN/PL11...; "
                        + "Dost. 100,00 PLN</td></tr>"
                        + "<tr><td class=\"td\"><nobr>10:05</nobr></td><td class=\"td\">mBank: Przelew przych. z rach. "
                        + "1111...222222 na rach. 33334444 kwota 50,00 PLN od Jan Testowy .; /OPF/AN/PL11...; "
                        + "Dost. 150,00 PLN</td></tr>");

        List<BankTransaction> transactions = parser.parse(html);

        assertEquals(2, transactions.size());
        assertNotEquals(transactions.get(0).bankReference(), transactions.get(1).bankReference());
    }

    @Test
    void unrecognizedHtmlYieldsNoTransactionsInsteadOfThrowing() {
        List<BankTransaction> transactions = parser.parse("<html><body><h1 class=\"h1\">2026-09-15 - x</h1></body></html>");

        assertTrue(transactions.isEmpty());
    }

    @Test
    void missingHeadingDateFailsLoudlyRatherThanSilentlyMisdatingTransactions() {
        String html = "<html><body><table><tr><th class=\"th\">Opis operacji</th></tr>"
                + "<tr><td class=\"td\">00:00</td><td class=\"td\">mBank: Przelew przych. kwota 10,00 PLN od X .; ref; Dost. 1,00 PLN</td></tr>"
                + "</table></body></html>";

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> parser.parse(html));
    }

    private static String notificationHtml(String date, String operationRows) {
        return "<html><body>"
                + "<h1 class=\"h1\">" + date + " - Powiadomienie e-mail</h1>"
                + "<table><tr><th class=\"th\">Czas operacji</th><th class=\"th\">Opis operacji</th></tr>"
                + operationRows
                + "</table></body></html>";
    }

    private static String readFixture() throws IOException {
        return readFixture("sample-statement.html");
    }

    private static String readFixture(String fileName) throws IOException {
        try (InputStream in = MBankStatementHtmlParserTest.class.getResourceAsStream("/mbank/" + fileName)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

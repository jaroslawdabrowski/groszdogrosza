package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.adapter.out.bankstatement.mbank;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain.BankTransaction;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises the parser against {@code src/test/resources/mbank/sample-statement.html},
 * which is currently only a PLACEHOLDER fixture matching the parser's own guessed
 * selectors (see both the fixture's and {@link MBankStatementHtmlParser}'s header
 * comments) - this test proves the parsing mechanics work, NOT that the selectors match a
 * real mBank export. Replace the fixture with a real (redacted) sample and this test
 * should keep passing unmodified if the parser is then fixed up to match it.
 */
class MBankStatementHtmlParserTest {

    @Test
    void parsesTransactionRowsFromThePlaceholderFixture() throws IOException {
        String html = readFixture();
        MBankStatementHtmlParser parser = new MBankStatementHtmlParser();

        List<BankTransaction> transactions = parser.parse(html);

        assertEquals(2, transactions.size());
        assertEquals("Jan Kowalski", transactions.get(0).senderName());
        assertEquals(new BigDecimal("50.00"), transactions.get(0).amount());
        assertEquals("Anna Nowak", transactions.get(1).senderName());
    }

    @Test
    void unrecognizedHtmlYieldsNoTransactionsInsteadOfThrowing() {
        MBankStatementHtmlParser parser = new MBankStatementHtmlParser();

        List<BankTransaction> transactions = parser.parse("<html><body>not a statement</body></html>");

        assertEquals(0, transactions.size());
    }

    private static String readFixture() throws IOException {
        try (InputStream in = MBankStatementHtmlParserTest.class.getResourceAsStream("/mbank/sample-statement.html")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

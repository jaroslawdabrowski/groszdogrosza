package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.out;

import io.github.jaroslawdabrowski.groszdogrosza.bankstatement.domain.BankTransaction;
import java.util.List;

/**
 * Parses one statement attachment's raw HTML into transactions. Implemented by
 * {@code adapter.out.bankstatement.mbank.MBankStatementHtmlParser} - kept as its own port
 * (rather than folded into {@link BankStatementFetchPort}) so a future second bank/format
 * can be added as an alternative implementation without touching the fetch/IMAP side.
 */
public interface StatementParserPort {

    List<BankTransaction> parse(String htmlContent);
}

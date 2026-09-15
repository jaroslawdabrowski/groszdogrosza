package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.in;

/**
 * Triggers one poll-and-process cycle: fetch new statement mail, parse transactions,
 * match parents, book money, all automatically. Invoked either by
 * {@code adapter.in.scheduler.BankStatementPollResource} (prod, called by AWS EventBridge
 * Scheduler) or {@code adapter.in.scheduler.BankStatementDevPoller} (dev-mode-only
 * {@code @Scheduled} fallback - see CLAUDE.md for why Lambda can't use @Scheduled directly).
 */
public interface PollBankStatementsUseCase {

    PollResult pollAndProcess();

    record PollResult(int transactionsSeen, int transactionsMatched, int transactionsUnmatched,
            int transactionsSkippedAlreadyProcessed, int transactionsFailed, int transactionsIgnoredTreasurerOwnAccount) {
    }
}

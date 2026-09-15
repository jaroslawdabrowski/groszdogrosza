package io.github.jaroslawdabrowski.groszdogrosza.bankstatement.port.out;

/**
 * Idempotency guard: claims a bank transaction reference before it is booked, so the same
 * transaction is never applied twice even if it appears again in a later statement mail
 * (e.g. overlapping date ranges) or the poll runs twice concurrently.
 */
public interface ProcessedTransactionRepositoryPort {

    /**
     * Atomically claims {@code bankReference} for processing. Returns {@code true} if this
     * call is the one that claimed it (the caller must now book it), {@code false} if it was
     * already claimed by an earlier call (the caller must skip it). Must be a single
     * conditional write, not a separate read-then-write - see
     * {@code bankstatement.application.BankStatementProcessingService} for why: claiming
     * happens immediately before booking, not before matching, so an unmatched transaction
     * stays unclaimed and is retried on the next poll.
     */
    boolean claimProcessing(String bankReference);
}

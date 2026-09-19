package io.github.jaroslawdabrowski.groszdogrosza.ledger.domain;

import java.math.BigDecimal;

/**
 * Formats a money amount for a {@link LedgerEntry}'s {@code params} - the one place in this
 * app that needs this, since every other amount shown in the UI is a JSON number field that
 * the frontend interpolates directly (and JS's own JSON parsing already drops meaningless
 * trailing zeros, e.g. {@code 40.00} becomes the number {@code 40}). A ledger param is a
 * String, not a JSON number, so a raw {@link BigDecimal#toPlainString()} keeps whatever
 * scale the value happened to carry - harmless for a treasurer-typed amount like
 * {@code BigDecimal("50")}, but every settlement/allocation amount in this app is computed
 * in integer grosz ({@link io.github.jaroslawdabrowski.groszdogrosza.collection.domain.SettlementPolicy}'s
 * {@code fromGrosz}) and always comes out at scale 2, so it would always print a trailing
 * {@code .00} even for a whole number of złoty - inconsistent with the balance/amount
 * sitting right next to it on the same page. {@link BigDecimal#stripTrailingZeros()} alone
 * isn't enough: for a value that's actually zero it can flip to a negative scale, which
 * {@link BigDecimal#toPlainString()} would then render back out as {@code "0E+1"}-shaped
 * padding - normalizing the scale back down to at least 0 avoids that.
 */
public final class LedgerAmounts {

    private LedgerAmounts() {
    }

    public static String format(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString();
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

public enum ContributionSource {
    /** Auto-matched and booked from a parsed mBank statement transaction. */
    BANK_STATEMENT_AUTO,
    /** Entered by the treasurer by hand (cash handed over in person, etc.). */
    MANUAL,
    /** Moved from a parent's piggy bank balance towards this collection's requirement. */
    PIGGY_BANK_APPLIED
}

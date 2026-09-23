package io.github.jaroslawdabrowski.groszdogrosza.collection.port.in;

/**
 * Sweeps as much of a student's CURRENT piggy bank balance as needed into their outstanding
 * requirements on currently ACTIVE collections, oldest collection first - the exact same
 * "landing spot" logic {@code bankstatement.application.BankStatementProcessingService}
 * already runs after an auto-matched bank transfer (see
 * {@code bankstatement.domain.ContributionAllocationPolicy}), exposed here so a manual
 * piggy-bank credit gets the same treatment. Without this, a cash payment recorded via
 * {@code CreditStudentPiggyBankManuallyUseCase} just sat in the balance until the next
 * collection happened to be created or the student was added back to one - a real gap a
 * treasurer hit in production (crediting cash for a student who already owed money on an
 * ACTIVE collection left that collection's requirement untouched).
 *
 * <p>Call this AFTER the balance has already been credited - it reads the student's balance
 * as it stands right now, it doesn't take an amount of its own.
 */
public interface SweepPiggyBankIntoActiveCollectionsUseCase {

    void sweep(String studentId);
}

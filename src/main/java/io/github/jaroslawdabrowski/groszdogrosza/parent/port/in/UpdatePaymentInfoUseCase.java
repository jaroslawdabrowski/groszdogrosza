package io.github.jaroslawdabrowski.groszdogrosza.parent.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;

/**
 * Treasurer-only: sets/updates the bank account number and BLIK phone number shown on the
 * public collection overview page - see {@code PaymentInfo}.
 */
public interface UpdatePaymentInfoUseCase {

    Parent updatePaymentInfo(String parentId, String bankAccountNumber, String blikPhoneNumber);
}

package io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain;

/** Either field may be null - a class may take bank transfers, BLIK, or both. */
public record PaymentInfo(String bankAccountNumber, String blikPhoneNumber) {

    public boolean isEmpty() {
        return isBlank(bankAccountNumber) && isBlank(blikPhoneNumber);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

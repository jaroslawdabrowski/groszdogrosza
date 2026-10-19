package io.github.jaroslawdabrowski.groszdogrosza.schoolclass.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.SchoolClass;

public interface UpdateClassPaymentInfoUseCase {

    SchoolClass updatePaymentInfo(String classId, String bankAccountNumber, String blikPhoneNumber);
}

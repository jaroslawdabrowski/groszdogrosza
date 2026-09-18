package io.github.jaroslawdabrowski.groszdogrosza.parent.port.in;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.PaymentInfo;
import java.util.Optional;

/**
 * Backs the unauthenticated public collection overview page - see
 * {@code platform.web.PublicOverviewResource}. Empty if no {@code Parent} with
 * {@code role=TREASURER} has configured payment info yet.
 */
public interface GetTreasurerPaymentInfoUseCase {

    Optional<PaymentInfo> getTreasurerPaymentInfo();
}

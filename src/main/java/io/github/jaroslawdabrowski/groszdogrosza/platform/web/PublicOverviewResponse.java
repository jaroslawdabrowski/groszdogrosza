package io.github.jaroslawdabrowski.groszdogrosza.platform.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web.CollectionProgressResponse;
import io.github.jaroslawdabrowski.groszdogrosza.schoolclass.domain.PaymentInfo;
import java.util.List;

/**
 * A class at a glance: its name, how to pay, and every active collection as aggregate
 * progress only (never per-student amounts or names). Served both on the public link
 * (anyone) and to the class's own members on Start.
 */
public record PublicOverviewResponse(
        String className, PaymentInfoResponse paymentInfo, List<CollectionProgressResponse> activeCollections) {

    public record PaymentInfoResponse(String bankAccountNumber, String blikPhoneNumber) {

        static PaymentInfoResponse from(PaymentInfo paymentInfo) {
            return paymentInfo == null || paymentInfo.isEmpty()
                    ? null
                    : new PaymentInfoResponse(paymentInfo.bankAccountNumber(), paymentInfo.blikPhoneNumber());
        }
    }
}

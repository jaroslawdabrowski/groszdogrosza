package io.github.jaroslawdabrowski.groszdogrosza.platform.web;

import io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web.CollectionProgressResponse;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.PaymentInfo;
import java.util.List;

/**
 * The unauthenticated public page: every currently ACTIVE collection's aggregate progress
 * (never a per-parent breakdown - see {@link CollectionProgressResponse}) plus how to pay
 * (the treasurer's account number / BLIK phone, if configured). See
 * {@code PublicOverviewResource} and CLAUDE.md ("Public collection overview").
 */
public record PublicOverviewResponse(PaymentInfoResponse paymentInfo, List<CollectionProgressResponse> activeCollections) {

    public record PaymentInfoResponse(String bankAccountNumber, String blikPhoneNumber) {

        static PaymentInfoResponse from(PaymentInfo paymentInfo) {
            return new PaymentInfoResponse(paymentInfo.bankAccountNumber(), paymentInfo.blikPhoneNumber());
        }
    }
}

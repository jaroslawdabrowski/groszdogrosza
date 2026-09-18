package io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;

public record ParentResponse(
        String id, String studentId, String firstName, String lastName, String email, String expectedSenderName,
        String role, String bankAccountNumber, String blikPhoneNumber) {

    public static ParentResponse from(Parent parent) {
        return new ParentResponse(parent.id(), parent.studentId(), parent.firstName(), parent.lastName(), parent.email(),
                parent.expectedSenderName(), parent.role().name(),
                parent.paymentInfo() == null ? null : parent.paymentInfo().bankAccountNumber(),
                parent.paymentInfo() == null ? null : parent.paymentInfo().blikPhoneNumber());
    }
}

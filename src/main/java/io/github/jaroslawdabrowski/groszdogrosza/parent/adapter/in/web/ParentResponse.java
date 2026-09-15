package io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import java.math.BigDecimal;

public record ParentResponse(
        String id, String firstName, String lastName, String email, String expectedSenderName,
        String role, BigDecimal piggyBankBalance) {

    static ParentResponse from(Parent parent) {
        return new ParentResponse(parent.id(), parent.firstName(), parent.lastName(), parent.email(),
                parent.expectedSenderName(), parent.role().name(), parent.piggyBankBalance());
    }
}

package io.github.jaroslawdabrowski.groszdogrosza.student.adapter.in.web;

import io.github.jaroslawdabrowski.groszdogrosza.parent.adapter.in.web.ParentResponse;
import io.github.jaroslawdabrowski.groszdogrosza.parent.domain.Parent;
import io.github.jaroslawdabrowski.groszdogrosza.student.domain.Student;
import java.math.BigDecimal;
import java.util.List;

/** @param parents 0-2 entries - see {@code Parent}'s javadoc for the cap. */
public record StudentResponse(
        String id, String firstName, String lastName, BigDecimal piggyBankBalance, List<ParentResponse> parents) {

    public static StudentResponse from(Student student, List<Parent> parents) {
        return new StudentResponse(student.id(), student.firstName(), student.lastName(), student.piggyBankBalance(),
                parents.stream().map(ParentResponse::from).toList());
    }
}

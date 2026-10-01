package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewCollectionPolicyTest {

    private static final List<String> ONE_STUDENT = List.of("student-1");

    @Test
    void acceptsATitledCollectionWithAPositiveAmount() {
        NewCollectionPolicy.validate("Wycieczka do ZOO", new BigDecimal("45"), ONE_STUDENT);
    }

    @Test
    void rejectsABlankTitle() {
        assertThrows(InvalidCollectionException.class,
                () -> NewCollectionPolicy.validate("   ", new BigDecimal("45"), ONE_STUDENT));
        assertThrows(InvalidCollectionException.class,
                () -> NewCollectionPolicy.validate(null, new BigDecimal("45"), ONE_STUDENT));
    }

    @Test
    void rejectsAZeroOrNegativeAmount() {
        assertThrows(InvalidCollectionException.class,
                () -> NewCollectionPolicy.validate("Prezent", BigDecimal.ZERO, ONE_STUDENT));
        assertThrows(InvalidCollectionException.class,
                () -> NewCollectionPolicy.validate("Prezent", new BigDecimal("-5"), ONE_STUDENT));
        assertThrows(InvalidCollectionException.class,
                () -> NewCollectionPolicy.validate("Prezent", null, ONE_STUDENT));
    }

    @Test
    void rejectsACollectionWithNoStudents() {
        assertThrows(InvalidCollectionException.class,
                () -> NewCollectionPolicy.validate("Prezent", new BigDecimal("10"), List.of()));
    }
}

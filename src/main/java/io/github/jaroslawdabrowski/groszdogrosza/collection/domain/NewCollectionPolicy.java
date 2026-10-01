package io.github.jaroslawdabrowski.groszdogrosza.collection.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a new collection must have: a title, an amount per student above zero, and at least
 * one student in it. Added after a collection was created by accident with an empty title and
 * 0 zł (the form had no validation) - it showed up for every parent as an unnamed collection
 * asking for nothing. Only checked when creating: collections already stored are loaded as
 * they are. The description stays optional.
 */
public final class NewCollectionPolicy {

    private NewCollectionPolicy() {
    }

    public static void validate(String title, BigDecimal baseAmountPerStudent, List<String> includedStudentIds) {
        if (title == null || title.isBlank()) {
            throw new InvalidCollectionException("A collection needs a title");
        }
        if (baseAmountPerStudent == null || baseAmountPerStudent.signum() <= 0) {
            throw new InvalidCollectionException("The amount per student must be greater than 0");
        }
        if (includedStudentIds == null || includedStudentIds.isEmpty()) {
            throw new InvalidCollectionException("A collection needs at least one student");
        }
    }
}

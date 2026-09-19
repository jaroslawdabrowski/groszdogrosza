package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;

/** @param studentIds which students this collection asks money from - see
 *                     {@code CreateCollectionUseCase}'s javadoc for why not every collection
 *                     includes every student. */
public record CreateCollectionRequest(String title, String description, BigDecimal baseAmountPerStudent, List<String> studentIds) {
}

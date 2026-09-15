package io.github.jaroslawdabrowski.groszdogrosza.collection.adapter.in.web;

import java.math.BigDecimal;

public record CreateCollectionRequest(String title, String description, BigDecimal baseAmountPerParent) {
}

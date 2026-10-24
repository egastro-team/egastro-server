package com.egastro.dto.response;

public record RecommendedProductResponse(ProductResponse product, Double score, RuleMetrics metrics) {
}

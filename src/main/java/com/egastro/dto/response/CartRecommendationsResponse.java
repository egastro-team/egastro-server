package com.egastro.dto.response;

import java.util.List;

public record CartRecommendationsResponse(String source, List<RecommendedProductResponse> items) {
}

package com.egastro.service.recommendation;

import com.egastro.dto.response.RuleMetrics;

public record ScoredRecommendation(long productId, Double score, RuleMetrics metrics) {
}

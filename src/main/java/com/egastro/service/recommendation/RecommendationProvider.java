package com.egastro.service.recommendation;

import java.util.List;

public interface RecommendationProvider {
    /** Returns at most limit entries, best first, without duplicates or cart ids.
     * Throws RecommendationUnavailableException only when no result can be produced. */
    List<ScoredRecommendation> recommend(List<Long> cartProductIds, int limit);
}

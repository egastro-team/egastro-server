package com.egastro.service.recommendation;

import com.egastro.config.properties.RecommendationProperties;
import com.egastro.dto.response.RuleMetrics;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class MlRecommendationProvider implements RecommendationProvider {
    private final RestClient recommendationRestClient;
    private final RecommendationProperties properties;

    @Override
    public List<ScoredRecommendation> recommend(List<Long> cartProductIds, int limit) {
        try {
            String baseUrl = properties.ml().baseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("ML service URL is blank");
            }
            var request = new ModelRequest(cartProductIds.stream().distinct().limit(50).toList(),
                    Math.max(1, Math.min(limit, 50)));
            var response = recommendationRestClient.post()
                    .uri(baseUrl.strip().replaceAll("/+$", "") + "/v1/recommendations")
                    .contentType(MediaType.APPLICATION_JSON).body(request)
                    .retrieve().onStatus(status -> !status.is2xxSuccessful(), (httpRequest, httpResponse) -> {
                        throw new IllegalStateException("Non-success recommendation status");
                    }).body(ModelResponse.class);
            if (response == null || response.recommendations() == null) {
                throw new IllegalStateException("Missing recommendations body");
            }
            Set<Long> excluded = new HashSet<>(cartProductIds);
            Map<Long, ScoredRecommendation> unique = new HashMap<>();
            for (var item : response.recommendations()) {
                if (item == null || item.productId() == null || item.productId() <= 0
                        || item.score() == null || !Double.isFinite(item.score())
                        || excluded.contains(item.productId())) {
                    continue;
                }
                var candidate = new ScoredRecommendation(item.productId(), item.score(), item.metrics());
                unique.merge(item.productId(), candidate,
                        (first, next) -> next.score() > first.score() ? next : first);
            }
            return unique.values().stream()
                    .sorted(Comparator.comparing(ScoredRecommendation::score).reversed()
                            .thenComparingLong(ScoredRecommendation::productId))
                    .limit(Math.max(0, limit)).toList();
        } catch (Exception exception) {
            // Client exception messages can contain response payloads or request URLs.
            var unavailable = new RecommendationUnavailableException(
                    "Recommendation service unavailable (" + exception.getClass().getSimpleName() + ")");
            log.warn("{}", unavailable.getMessage());
            throw unavailable;
        }
    }

    private record ModelRequest(@JsonProperty("cart_item_ids") List<Long> cartItemIds,
                                @JsonProperty("max_k") int maxK) { }
    private record ModelResponse(List<ModelItem> recommendations) { }
    private record ModelItem(@JsonProperty("product_id") Long productId, Double score, RuleMetrics metrics) { }
}

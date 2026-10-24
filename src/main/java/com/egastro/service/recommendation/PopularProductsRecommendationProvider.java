package com.egastro.service.recommendation;

import com.egastro.repository.OrderItemRepository;
import com.egastro.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class PopularProductsRecommendationProvider implements RecommendationProvider {
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    @Override
    public List<ScoredRecommendation> recommend(List<Long> cartProductIds, int limit) {
        try {
            if (limit <= 0) {
                return List.of();
            }
            Set<Long> excluded = new HashSet<>(cartProductIds);
            excluded.add(-1L); // Keep NOT IN valid even for an empty cart.
            var ids = new ArrayList<>(orderItemRepository.findPopularProductIds(excluded, PageRequest.of(0, limit)));
            if (ids.size() < limit) {
                excluded.addAll(ids);
                ids.addAll(productRepository.findAvailableIdsExcluding(excluded, PageRequest.of(0, limit - ids.size())));
            }
            return ids.stream().map(id -> new ScoredRecommendation(id, null, null)).toList();
        } catch (Exception exception) {
            log.warn("Popular products unavailable ({})", exception.getClass().getSimpleName());
            return List.of();
        }
    }
}

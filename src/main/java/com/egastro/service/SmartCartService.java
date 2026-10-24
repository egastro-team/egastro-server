package com.egastro.service;

import com.egastro.config.properties.RecommendationProperties;
import com.egastro.dto.response.*;
import com.egastro.mapper.ProductMapper;
import com.egastro.repository.ProductRepository;
import com.egastro.service.recommendation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SmartCartService {
    private final CartService cartService;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final int defaultLimit;
    private final int maxLimit;
    private final RecommendationProvider primary;
    private final RecommendationProvider fallback;

    public SmartCartService(CartService cartService, ProductRepository productRepository, ProductMapper productMapper,
                            RecommendationProperties properties,
                            @Qualifier("mlRecommendationProvider") RecommendationProvider primary,
                            @Qualifier("popularProductsRecommendationProvider") RecommendationProvider fallback) {
        this.cartService = cartService;
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.defaultLimit = properties.defaultLimit();
        this.maxLimit = properties.maxLimit();
        this.primary = primary;
        this.fallback = fallback;
    }

    public CartRecommendationsResponse recommend(Long userId, Integer requestedLimit) {
        int limit = requestedLimit == null ? defaultLimit
                : Math.max(1, Math.min(requestedLimit, maxLimit));
        var cartIds = cartService.getItems(userId).stream().map(item -> item.product().getId()).distinct().toList();
        if (cartIds.isEmpty()) {
            return new CartRecommendationsResponse("MODEL", List.of());
        }
        try {
            var items = load(primary.recommend(cartIds, (int) Math.min(limit * 2L, 50)), cartIds, limit);
            if (!items.isEmpty()) {
                return new CartRecommendationsResponse("MODEL", items);
            }
        } catch (RecommendationUnavailableException ignored) {
            // The provider logs unavailability without exposing cart contents.
        }
        try {
            return new CartRecommendationsResponse("FALLBACK", load(fallback.recommend(cartIds, limit), cartIds, limit));
        } catch (Exception ignored) {
            return new CartRecommendationsResponse("FALLBACK", List.of());
        }
    }

    private List<RecommendedProductResponse> load(List<ScoredRecommendation> candidates, List<Long> cartIds, int limit) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        var products = productRepository.findAllByIdIn(candidates.stream().map(ScoredRecommendation::productId).toList())
                .stream().collect(Collectors.toMap(product -> product.getId(), Function.identity()));
        Set<Long> excluded = new HashSet<>(cartIds);
        var items = new ArrayList<RecommendedProductResponse>();
        for (var candidate : candidates) {
            var product = products.get(candidate.productId());
            if (product != null && product.isAvailable() && excluded.add(product.getId())) {
                items.add(new RecommendedProductResponse(productMapper.toResponse(product), candidate.score(), candidate.metrics()));
                if (items.size() == limit) {
                    break;
                }
            }
        }
        return items;
    }
}

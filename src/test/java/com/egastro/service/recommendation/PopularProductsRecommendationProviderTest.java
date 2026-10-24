package com.egastro.service.recommendation;

import com.egastro.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PopularProductsRecommendationProviderTest {
    @Mock private OrderItemRepository orders;
    @Mock private ProductRepository products;
    @InjectMocks private PopularProductsRecommendationProvider provider;

    @Test
    void shouldKeepPopularityOrder_When_LimitIsFilled() {
        // GIVEN
        when(orders.findPopularProductIds(Set.of(-1L, 3L), PageRequest.of(0, 2))).thenReturn(List.of(7L, 6L));
        // WHEN
        var result = provider.recommend(List.of(3L), 2);
        // THEN
        assertEquals(List.of(new ScoredRecommendation(7, null, null), new ScoredRecommendation(6, null, null)), result);
        verifyNoInteractions(products);
    }

    @Test
    void shouldFillRemainderWithoutDuplicates_When_HistoryIsSparse() {
        // GIVEN
        when(orders.findPopularProductIds(Set.of(-1L, 3L), PageRequest.of(0, 3))).thenReturn(List.of(7L));
        when(products.findAvailableIdsExcluding(Set.of(-1L, 3L, 7L), PageRequest.of(0, 2))).thenReturn(List.of(9L, 8L));
        // WHEN
        var result = provider.recommend(List.of(3L), 3);
        // THEN
        assertEquals(List.of(7L, 9L, 8L), result.stream().map(ScoredRecommendation::productId).toList());
    }

    @Test
    void shouldReturnEmpty_When_NoHistoryOrAvailableProducts() {
        // GIVEN
        when(orders.findPopularProductIds(Set.of(-1L), PageRequest.of(0, 2))).thenReturn(List.of());
        when(products.findAvailableIdsExcluding(Set.of(-1L), PageRequest.of(0, 2))).thenReturn(List.of());
        // WHEN
        var result = provider.recommend(List.of(), 2);
        // THEN
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNeverThrow_When_DatabaseFails() {
        // GIVEN
        when(orders.findPopularProductIds(any(), any())).thenThrow(new IllegalStateException());
        // WHEN
        var result = provider.recommend(List.of(3L), 2);
        // THEN
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipQueries_When_LimitIsZero() {
        // GIVEN
        var ids = List.of(3L);
        // WHEN
        var result = provider.recommend(ids, 0);
        // THEN
        assertTrue(result.isEmpty());
        verifyNoInteractions(orders, products);
    }
}

package com.egastro.service;

import com.egastro.dto.response.RecommendedProductResponse;
import com.egastro.mapper.ProductMapper;
import com.egastro.repository.ProductRepository;
import com.egastro.service.recommendation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static com.egastro.testData.RecommendationTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartCartServiceTest {
    @Mock private CartService cartService;
    @Mock private ProductRepository products;
    @Mock private ProductMapper mapper;
    @Mock private RecommendationProvider primary;
    @Mock private RecommendationProvider fallback;
    private SmartCartService service;

    @BeforeEach
    void setUp() { service = new SmartCartService(cartService, products, mapper, properties(), primary, fallback); }

    @Test
    void shouldSkipProviders_When_CartIsEmpty() {
        // GIVEN
        when(cartService.getItems(1L)).thenReturn(List.of());
        // WHEN
        var result = service.recommend(1L, null);
        // THEN
        assertEquals("MODEL", result.source());
        assertTrue(result.items().isEmpty());
        verifyNoInteractions(primary, fallback, products, mapper);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.RecommendationTestData#limits")
    void shouldClampAndDefaultLimit_When_Requested(Integer limit, int candidates) {
        // GIVEN
        when(cartService.getItems(1L)).thenReturn(cartItems());
        when(primary.recommend(List.of(3L), candidates)).thenReturn(List.of());
        when(fallback.recommend(eq(List.of(3L)), anyInt())).thenReturn(List.of());
        // WHEN
        var result = service.recommend(1L, limit);
        // THEN
        assertEquals("FALLBACK", result.source());
        verify(primary).recommend(List.of(3L), candidates);
        verify(fallback).recommend(List.of(3L), candidates / 2);
        verifyNoInteractions(products);
    }

    @Test
    void shouldFilterAndPreserveOrderWithoutTopUp_When_ModelHasValidCandidates() {
        // GIVEN
        when(cartService.getItems(1L)).thenReturn(cartItems());
        var candidates = List.of(scored(99, 10), scored(3, 9), scored(4, 8), scored(7, 7), scored(7, 6), scored(6, 5), scored(8, 4));
        when(primary.recommend(List.of(3L), 4)).thenReturn(candidates);
        var seven = product(7, true);
        var six = product(6, true);
        when(products.findAllByIdIn(any())).thenReturn(List.of(six, seven, product(4, false), product(3, true), product(8, true)));
        when(mapper.toResponse(seven)).thenReturn(com.egastro.testData.ResponseTestData.productResponse());
        // WHEN
        var result = service.recommend(1L, 2);
        // THEN
        assertEquals("MODEL", result.source());
        assertEquals(List.of(7.0, 5.0), result.items().stream().map(RecommendedProductResponse::score).toList());
        assertEquals(candidates.get(3).metrics(), result.items().getFirst().metrics());
        verify(products, times(1)).findAllByIdIn(any());
        verify(mapper).toResponse(six);
        verifyNoInteractions(fallback);
    }

    @Test
    void shouldReturnPartialModelResult_When_FewerThanLimitSurvive() {
        // GIVEN
        when(cartService.getItems(1L)).thenReturn(cartItems());
        when(primary.recommend(List.of(3L), 10)).thenReturn(List.of(scored(7, 1)));
        when(products.findAllByIdIn(List.of(7L))).thenReturn(List.of(product(7, true)));
        // WHEN
        var result = service.recommend(1L, null);
        // THEN
        assertEquals("MODEL", result.source());
        assertEquals(1, result.items().size());
        verifyNoInteractions(fallback);
    }

    @Test
    void shouldCapModelCandidatesAtFifty_When_MaxLimitIsConfiguredHigher() {
        // GIVEN
        service = new SmartCartService(cartService, products, mapper,
                new com.egastro.config.properties.RecommendationProperties(properties().ml(), 5, 100), primary, fallback);
        when(cartService.getItems(1L)).thenReturn(cartItems());
        when(primary.recommend(List.of(3L), 50)).thenReturn(List.of());
        when(fallback.recommend(List.of(3L), 100)).thenReturn(List.of());
        // WHEN
        var result = service.recommend(1L, 100);
        // THEN
        assertEquals("FALLBACK", result.source());
        verify(primary).recommend(List.of(3L), 50);
        verify(fallback).recommend(List.of(3L), 100);
    }

    @Test
    void shouldUseFallback_When_AllModelProductsAreFiltered() {
        // GIVEN
        when(cartService.getItems(1L)).thenReturn(cartItems());
        when(primary.recommend(List.of(3L), 10)).thenReturn(List.of(scored(99, 1)));
        when(products.findAllByIdIn(List.of(99L))).thenReturn(List.of());
        when(fallback.recommend(List.of(3L), 5)).thenReturn(List.of(new ScoredRecommendation(7, null, null)));
        when(products.findAllByIdIn(List.of(7L))).thenReturn(List.of(product(7, true)));
        // WHEN
        var result = service.recommend(1L, null);
        // THEN
        assertEquals("FALLBACK", result.source());
        assertEquals(1, result.items().size());
        assertNull(result.items().getFirst().score());
        assertNull(result.items().getFirst().metrics());
    }

    @Test
    void shouldReturnEmptyFallback_When_PrimaryAndFallbackFail() {
        // GIVEN
        when(cartService.getItems(1L)).thenReturn(cartItems());
        when(primary.recommend(List.of(3L), 10)).thenThrow(new RecommendationUnavailableException("offline"));
        when(fallback.recommend(List.of(3L), 5)).thenThrow(new IllegalStateException());
        // WHEN
        var result = service.recommend(1L, null);
        // THEN
        assertEquals("FALLBACK", result.source());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void shouldReturnEmptyFallback_When_FallbackProductLoadFails() {
        // GIVEN
        when(cartService.getItems(1L)).thenReturn(cartItems());
        when(primary.recommend(List.of(3L), 10)).thenReturn(List.of());
        when(fallback.recommend(List.of(3L), 5)).thenReturn(List.of(scored(7, 1)));
        when(products.findAllByIdIn(any())).thenThrow(new IllegalStateException());
        // WHEN
        var result = service.recommend(1L, null);
        // THEN
        assertEquals("FALLBACK", result.source());
        assertTrue(result.items().isEmpty());
    }
}

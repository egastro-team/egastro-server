package com.egastro.service;

import com.egastro.enums.ProductSort;
import com.egastro.enums.UnitOfMeasure;
import com.egastro.event.ProductDeletedEvent;
import com.egastro.exception.ApiException;
import com.egastro.mapper.ProductMapper;
import com.egastro.repository.CategoryRepository;
import com.egastro.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;

import static com.egastro.testData.TestFixtures.*;
import static com.egastro.testData.RequestTestData.replacementProductRequest;
import static com.egastro.support.TestAssertions.assertApiError;
import static com.egastro.testData.ResponseTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock private ProductRepository products;
    @Mock private CategoryRepository categories;
    @Mock private ApplicationEventPublisher events;
    @Spy private ProductMapper mapper;
    @InjectMocks private ProductService service;

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#catalogSearches")
    void shouldEscapeSearchAndMapPage_When_BrowsingWithoutCategory(String query) {
        // GIVEN
        when(products.findCatalog(isNull(), anyString(), any())).thenAnswer(invocation ->
                productPage(invocation.getArgument(2)));

        // WHEN
        var response = service.list(null, query, null, -1, 1000);

        // THEN
        var pattern = ArgumentCaptor.forClass(String.class);
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(products, times(1)).findCatalog(isNull(), pattern.capture(), page.capture());
        String expected = query == null || query.isEmpty() ? "%%"
                : query.equals(" Milk ") ? "%milk%" : query.equals("x") ? "%x%" : "%\\%\\_\\\\%";
        assertEquals(expected, pattern.getValue());
        assertEquals(0, page.getValue().getPageNumber());
        assertEquals(100, page.getValue().getPageSize());
        assertEquals(ProductSort.NAME_ASC.getSort(), page.getValue().getSort());
        assertEquals("Milk", response.content().getFirst().name());
        assertEquals(1, response.totalElements());
        verifyNoInteractions(categories);
    }

    @Test
    void shouldFilterCategory_When_CategoryExists() {
        // GIVEN
        when(categories.existsById(2L)).thenReturn(true);
        when(products.findCatalog(eq(2L), eq("%%"), any())).thenAnswer(invocation ->
                emptyProductPage(invocation.getArgument(2)));

        // WHEN
        var result = service.list(2L, null, "price_desc", 0, 20);

        // THEN
        assertTrue(result.content().isEmpty());
        assertEquals(0, result.totalPages());
        verify(categories, times(1)).existsById(2L);
    }

    @Test
    void shouldRejectFilter_When_CategoryMissing() {
        // GIVEN
        Long categoryId = 2L;

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.list(categoryId, null, null, 0, 20));

        // THEN
        assertApiError(error, NOT_FOUND, "Category not found");
        verifyNoInteractions(products);
    }

    @Test
    void shouldRejectSearch_When_TrimmedLengthExceedsLimit() {
        // GIVEN
        String search = "x".repeat(101);

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.list(null, search, null, 0, 20));

        // THEN
        assertApiError(error, BAD_REQUEST, "Validation failed");
        assertEquals("q", error.getFieldErrors().getFirst().field());
        verifyNoInteractions(products);
    }

    @Test
    void shouldAcceptSearch_When_LengthIsExactlyMaximum() {
        // GIVEN
        String query = "x".repeat(100);
        when(products.findCatalog(isNull(), eq("%" + query + "%"), any())).thenReturn(emptyProductPage());

        // WHEN
        var result = service.list(null, " " + query + " ", null, 0, 20);

        // THEN
        assertTrue(result.content().isEmpty());
    }

    @Test
    void shouldReturnDetails_When_ProductExists() {
        // GIVEN
        when(products.findById(3L)).thenReturn(Optional.of(product()));

        // WHEN
        var result = service.get(3L);

        // THEN
        assertEquals(3L, result.id());
        assertEquals("Dairy", result.categoryName());
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#productOperations")
    void shouldRejectOperation_When_ProductMissing(String operation) {
        // GIVEN
        var request = replacementProductRequest();

        // WHEN
        var error = assertThrows(ApiException.class, () -> {
            switch (operation) {
                case "get" -> service.get(3L);
                case "update" -> service.update(3L, request);
                default -> service.delete(3L);
            }
        });

        // THEN
        assertApiError(error, NOT_FOUND, "Product not found");
        verifyNoInteractions(events);
        verify(products, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#booleanValues")
    void shouldReplaceAllFields_When_CreatingOrUpdatingProduct(boolean update) {
        // GIVEN
        if (update) when(products.findById(3L)).thenReturn(Optional.of(product()));
        when(categories.findById(2L)).thenReturn(Optional.of(category()));
        when(products.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN
        var result = update ? service.update(3L, replacementProductRequest()) : service.create(replacementProductRequest());

        // THEN
        assertAll(() -> assertEquals("Cheese", result.name()),
                () -> assertEquals("Description", result.description()),
                () -> assertEquals(new BigDecimal("12.30"), result.price()),
                () -> assertEquals(UnitOfMeasure.KG, result.unitOfMeasure()),
                () -> assertNull(result.imageUrl()),
                () -> assertFalse(result.available()),
                () -> assertEquals(2L, result.categoryId()));
    }

    @Test
    void shouldRejectCreate_When_CategoryDoesNotExist() {
        // GIVEN
        var request = replacementProductRequest();

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.create(request));

        // THEN
        assertApiError(error, BAD_REQUEST, "Validation failed");
        assertEquals("categoryId", error.getFieldErrors().getFirst().field());
        verify(products, never()).save(any());
    }

    @Test
    void shouldPublishEventBeforeHardDelete_When_ProductExists() {
        // GIVEN
        when(products.findById(3L)).thenReturn(Optional.of(product()));

        // WHEN
        service.delete(3L);

        // THEN
        var order = inOrder(events, products);
        order.verify(events, times(1)).publishEvent(productDeletedEvent());
        order.verify(products, times(1)).deleteById(3L);
    }

    @Test
    void shouldAbortDeletion_When_ListenerFails() {
        // GIVEN
        when(products.findById(3L)).thenReturn(Optional.of(product()));
        doThrow(new IllegalStateException("cleanup failed")).when(events).publishEvent(any(ProductDeletedEvent.class));

        // WHEN
        var error = assertThrows(IllegalStateException.class, () -> service.delete(3L));

        // THEN
        assertEquals("cleanup failed", error.getMessage());
        verify(products, never()).deleteById(anyLong());
    }

}

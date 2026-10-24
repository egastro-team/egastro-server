package com.egastro.controller;

import com.egastro.exception.ApiException;
import com.egastro.service.ProductService;
import com.egastro.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.egastro.testData.ResponseTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@WebMvcTest(ProductController.class)
class ProductControllerIT extends WebTestSupport {
    @MockitoBean private ProductService products;

    @Test
    void shouldReturnProductPage_When_DefaultParametersUsed() throws Exception {
        // GIVEN
        var response = productPageResponse();
        when(products.list(null, null, null, 0, 20)).thenReturn(response);

        // WHEN
        var result = mvc.perform(get("/api/products")).andReturn();

        // THEN
        assertEquals(tree(response), body(result, 200));
        verify(products, times(1)).list(null, null, null, 0, 20);
    }

    @Test
    void shouldPassFiltersToService_When_QueryParametersProvided() throws Exception {
        // GIVEN
        var response = emptyProductPageResponse();
        when(products.list(2L, "milk", "price_asc", 2, 5)).thenReturn(response);

        // WHEN
        var result = mvc.perform(get("/api/products").param("category", "2").param("q", "milk")
                .param("sort", "price_asc").param("page", "2").param("size", "5")).andReturn();

        // THEN
        assertEquals(tree(response), body(result, 200));
    }

    @Test
    void shouldReturnProductDetails_When_ProductExists() throws Exception {
        // GIVEN
        var response = productResponse();
        when(products.get(3L)).thenReturn(response);

        // WHEN
        var result = mvc.perform(get("/api/products/3")).andReturn();

        // THEN
        assertEquals(tree(response), body(result, 200));
    }

    @Test
    void shouldReturnNotFound_When_ProductMissing() throws Exception {
        // GIVEN
        when(products.get(3L)).thenThrow(ApiException.notFound("Product not found"));

        // WHEN
        var result = mvc.perform(get("/api/products/3")).andReturn();

        // THEN
        assertFalse(error(result, 404, "Product not found", "/api/products/3").has("fieldErrors"));
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#numericCatalogParameters")
    void shouldRejectParameter_When_NumberIsMalformed(String parameter) throws Exception {
        // GIVEN
        String path = "/api/products";

        // WHEN
        var result = mvc.perform(get(path).param(parameter, "abc")).andReturn();

        // THEN
        fieldError(error(result, 400, "Validation failed", path), parameter, "Invalid parameter value");
        verifyNoInteractions(products);
    }

    @Test
    void shouldReturnSortFieldError_When_ServiceRejectsSort() throws Exception {
        // GIVEN
        when(products.list(null, null, "unknown", 0, 20))
                .thenThrow(ApiException.validation("sort", "Invalid product sort value"));

        // WHEN
        var result = mvc.perform(get("/api/products").param("sort", "unknown")).andReturn();

        // THEN
        fieldError(error(result, 400, "Validation failed", "/api/products"), "sort", "Invalid product sort value");
    }

    @Test
    void shouldAllowBrowserPreflight_When_OriginConfigured() throws Exception {
        // GIVEN
        var request = options("/api/products").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type");

        // WHEN
        var result = mvc.perform(request).andReturn();

        // THEN
        assertEquals(200, result.getResponse().getStatus());
        assertEquals("http://localhost:5173", result.getResponse().getHeader("Access-Control-Allow-Origin"));
        assertNull(result.getResponse().getHeader("Access-Control-Allow-Credentials"));
        verifyNoInteractions(products);
    }

    @Test
    void shouldRejectBrowserPreflight_When_OriginUnlisted() throws Exception {
        // GIVEN
        var request = options("/api/products").header("Origin", "https://unlisted.example.com")
                .header("Access-Control-Request-Method", "GET");

        // WHEN
        var result = mvc.perform(request).andReturn();

        // THEN
        assertEquals(403, result.getResponse().getStatus());
        assertNull(result.getResponse().getHeader("Access-Control-Allow-Origin"));
        verifyNoInteractions(products);
    }
}

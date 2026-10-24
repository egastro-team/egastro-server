package com.egastro.controller;

import com.egastro.dto.request.ProductRequest;
import com.egastro.exception.ApiException;
import com.egastro.service.ProductService;
import com.egastro.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.egastro.testData.ResponseTestData.productResponse;
import static com.egastro.testData.HttpTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@WebMvcTest(AdminProductController.class)
class AdminProductControllerIT extends WebTestSupport {
    @MockitoBean private ProductService products;

    @Test
    void shouldReturnCreatedWithLocation_When_AdminCreatesProduct() throws Exception {
        // GIVEN
        var expected = productResponse();
        when(products.create(any(ProductRequest.class))).thenReturn(expected);

        // WHEN
        var result = mvc.perform(admin(post("/api/admin/products"))
                .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON)).andReturn();

        // THEN
        assertEquals(tree(expected), body(result, 201));
        assertEquals("/api/products/3", result.getResponse().getHeader("Location"));
    }

    @Test
    void shouldReturnReplacement_When_AdminUpdatesProduct() throws Exception {
        // GIVEN
        var expected = productResponse();
        when(products.update(eq(3L), any())).thenReturn(expected);

        // WHEN
        var result = mvc.perform(admin(put("/api/admin/products/3"))
                .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON)).andReturn();

        // THEN
        assertEquals(tree(expected), body(result, 200));
    }

    @Test
    void shouldReturnNoContent_When_AdminDeletesProduct() throws Exception {
        // GIVEN
        String path = "/api/admin/products/3";

        // WHEN
        var result = mvc.perform(admin(delete(path))).andReturn();

        // THEN
        assertEquals(204, result.getResponse().getStatus());
        assertEquals("", result.getResponse().getContentAsString());
        verify(products, times(1)).delete(3L);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#adminMutationMethods")
    void shouldRejectNonAdmins_When_AdminProductRouteAccessed(String method) throws Exception {
        // GIVEN
        String path = "/api/admin/products" + (method.equals("POST") ? "" : "/3");
        var request = request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON)
                .content(PRODUCT_JSON);

        // WHEN
        var anonymous = mvc.perform(request).andReturn();
        var customer = mvc.perform(user(request)).andReturn();

        // THEN
        error(anonymous, 401, "Authentication required", path);
        error(customer, 403, "Access denied", path);
        verifyNoInteractions(products);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#adminWriteMethods")
    void shouldReturnValidationErrors_When_RequiredProductFieldsMissing(String method) throws Exception {
        // GIVEN
        String path = "/api/admin/products" + (method.equals("POST") ? "" : "/3");

        // WHEN
        var result = mvc.perform(admin(request(HttpMethod.valueOf(method), path))
                .contentType(MediaType.APPLICATION_JSON).content(EMPTY_JSON)).andReturn();

        // THEN
        fieldError(error(result, 400, "Validation failed", path), "name", "Name is required");
        verifyNoInteractions(products);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#adminMutationMethods")
    void shouldReturnBusinessFailure_When_ServiceRejectsProductMutation(String method) throws Exception {
        // GIVEN
        String path = "/api/admin/products" + (method.equals("POST") ? "" : "/3");
        String message = method.equals("POST") ? "Already exists" : "Resource missing";
        var failure = method.equals("POST") ? ApiException.conflict(message) : ApiException.notFound(message);
        switch (method) {
            case "POST" -> when(products.create(any())).thenThrow(failure);
            case "PUT" -> when(products.update(eq(3L), any())).thenThrow(failure);
            default -> doThrow(failure).when(products).delete(3L);
        }

        // WHEN
        var result = mvc.perform(admin(request(HttpMethod.valueOf(method), path))
                .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON)).andReturn();

        // THEN
        error(result, method.equals("POST") ? 409 : 404, message, path);
    }

    @Test
    void shouldRejectUnknownEnum_When_ProductBodyMalformed() throws Exception {
        // GIVEN
        String payload = productWithUnknownUnitJson();

        // WHEN
        var result = mvc.perform(admin(post("/api/admin/products"))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();

        // THEN
        error(result, 400, "Malformed request body", "/api/admin/products");
        verifyNoInteractions(products);
    }
}

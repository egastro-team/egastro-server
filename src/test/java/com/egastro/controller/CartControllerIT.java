package com.egastro.controller;

import com.egastro.exception.ApiException;
import com.egastro.service.CartService;
import com.egastro.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static com.egastro.testData.RequestTestData.*;
import static com.egastro.testData.ResponseTestData.*;
import static com.egastro.testData.HttpTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@WebMvcTest(CartController.class)
class CartControllerIT extends WebTestSupport {
    @MockitoBean private CartService service;

    @Test
    void shouldReturnCartWithAllFields_When_UserAuthenticated() throws Exception {
        // GIVEN
        var cart = cartResponse();
        when(service.get(1L)).thenReturn(cart);

        // WHEN
        var result = mvc.perform(user(get("/api/cart"))).andReturn();

        // THEN
        var response = body(result, 200);
        assertEquals(tree(cart), response);
        assertEquals(new BigDecimal("4.98"), response.path("totalAmount").decimalValue());
        verify(service, times(1)).get(1L);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#cartMutations")
    void shouldReturnUpdatedCart_When_MutationSucceeds(String method) throws Exception {
        // GIVEN
        var cart = emptyCartResponse();
        var request = switch (method) {
            case "POST" -> {
                when(service.addItem(1L, addCartItemRequest(3L, 2))).thenReturn(cart);
                yield post("/api/cart/items").content(ADD_TWO_ITEMS_JSON);
            }
            case "PUT" -> {
                when(service.updateItem(1L, 3L, updateCartItemRequest(2))).thenReturn(cart);
                yield put("/api/cart/items/3").content(QUANTITY_TWO_JSON);
            }
            default -> {
                when(service.removeItem(1L, 3L)).thenReturn(cart);
                yield delete("/api/cart/items/3");
            }
        };

        // WHEN
        var result = mvc.perform(user(request.contentType(MediaType.APPLICATION_JSON))).andReturn();

        // THEN
        assertEquals(tree(cart), body(result, 200));
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#invalidCartBodies")
    void shouldRejectBody_When_ValidationFails(String method, String payload, String field, String message) throws Exception {
        // GIVEN
        String path = method.equals("POST") ? "/api/cart/items" : "/api/cart/items/3";

        // WHEN
        var result = mvc.perform(user(request(HttpMethod.valueOf(method), path))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();

        // THEN
        fieldError(error(result, 400, "Validation failed", path), field, message);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#cartMethods")
    void shouldRejectAnonymousAndInvalidToken_When_CartAccessed(String method) throws Exception {
        // GIVEN
        String path = method.equals("GET") ? "/api/cart" : method.equals("POST") ? "/api/cart/items" : "/api/cart/items/3";
        var request = request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON)
                .content(ADD_ONE_ITEM_JSON);

        // WHEN
        var anonymous = mvc.perform(request).andReturn();
        var invalid = mvc.perform(request.header("Authorization", "Bearer invalid-token")).andReturn();

        // THEN
        assertFalse(error(anonymous, 401, "Authentication required", path).has("fieldErrors"));
        error(invalid, 401, "Authentication required", path);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#cartAddFailures")
    void shouldReturnBusinessError_When_AddRejected(int status, String message) throws Exception {
        // GIVEN
        when(service.addItem(eq(1L), any())).thenThrow(new ApiException(org.springframework.http.HttpStatus.valueOf(status), message));

        // WHEN
        var result = mvc.perform(user(post("/api/cart/items")).contentType(MediaType.APPLICATION_JSON)
                .content(ADD_ONE_ITEM_JSON)).andReturn();

        // THEN
        assertFalse(error(result, status, message, "/api/cart/items").has("fieldErrors"));
    }

    @Test
    void shouldReturnQuantityFieldError_When_AdditionOverflows() throws Exception {
        // GIVEN
        when(service.addItem(eq(1L), any())).thenThrow(ApiException.validation("quantity", "Maximum quantity per product is 99"));

        // WHEN
        var result = mvc.perform(user(post("/api/cart/items")).contentType(MediaType.APPLICATION_JSON)
                .content(ADD_ONE_ITEM_JSON)).andReturn();

        // THEN
        fieldError(error(result, 400, "Validation failed", "/api/cart/items"),
                "quantity", "Maximum quantity per product is 99");
    }

    @Test
    void shouldReturnNotFound_When_UpdatingAbsentItem() throws Exception {
        // GIVEN
        when(service.updateItem(eq(1L), eq(3L), any())).thenThrow(ApiException.notFound("Item not found in cart"));

        // WHEN
        var result = mvc.perform(user(put("/api/cart/items/3")).contentType(MediaType.APPLICATION_JSON)
                .content(QUANTITY_ONE_JSON)).andReturn();

        // THEN
        error(result, 404, "Item not found in cart", "/api/cart/items/3");
    }

    @Test
    void shouldRejectBadPathId_When_ProductIdIsNotNumeric() throws Exception {
        // GIVEN
        String path = "/api/cart/items/abc";

        // WHEN
        var result = mvc.perform(user(delete(path))).andReturn();

        // THEN
        fieldError(error(result, 400, "Validation failed", path), "productId", "Invalid parameter value");
        verifyNoInteractions(service);
    }
}

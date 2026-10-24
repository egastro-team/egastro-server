package com.egastro.controller;

import com.egastro.dto.request.CreateOrderRequest;
import com.egastro.enums.OrderStatus;
import com.egastro.exception.ApiException;
import com.egastro.service.OrderService;
import com.egastro.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.egastro.testData.OrderTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@WebMvcTest(OrderController.class)
class OrderControllerIT extends WebTestSupport {
    @MockitoBean private OrderService service;

    @Test
    void shouldCreateOrderWithLocationAndNormalizedInput_When_CheckoutValid() throws Exception {
        // GIVEN
        var expected = orderResponse(OrderStatus.NEW);
        when(service.create(1L, checkoutRequest())).thenReturn(expected);

        // WHEN
        var result = mvc.perform(user(post("/api/orders")).contentType(MediaType.APPLICATION_JSON)
                .content(CHECKOUT_JSON)).andReturn();

        // THEN
        assertEquals(tree(expected), body(result, 201));
        assertEquals("/api/orders/6", result.getResponse().getHeader("Location"));
        verify(service, times(1)).create(1L, checkoutRequest());
    }

    @Test
    void shouldReturnOwnHistoryWithDefaultPagination_When_ParametersOmitted() throws Exception {
        // GIVEN
        when(service.list(1L, 0, 20)).thenReturn(orderPage());

        // WHEN
        var result = mvc.perform(user(get("/api/orders"))).andReturn();

        // THEN
        assertEquals(tree(orderPage()), body(result, 200));
        verify(service).list(1L, 0, 20);
    }

    @Test
    void shouldDelegatePagination_When_ParametersSupplied() throws Exception {
        // GIVEN
        when(service.list(1L, -1, 101)).thenReturn(orderPage());

        // WHEN
        var result = mvc.perform(user(get("/api/orders?page=-1&size=101"))).andReturn();

        // THEN
        assertEquals(tree(orderPage()), body(result, 200));
        verify(service).list(1L, -1, 101);
    }

    @Test
    void shouldReturnOwnOrderDetails_When_OrderExists() throws Exception {
        // GIVEN
        when(service.get(1L, 6L)).thenReturn(orderResponse(OrderStatus.NEW));

        // WHEN
        var result = mvc.perform(user(get("/api/orders/6"))).andReturn();

        // THEN
        var response = body(result, 200);
        assertEquals(tree(orderResponse(OrderStatus.NEW)), response);
        assertFalse(response.has("userEmail"));
        assertFalse(response.has("passwordHash"));
    }

    @Test
    void shouldReturnCancelledOrder_When_CancellationSucceeds() throws Exception {
        // GIVEN
        when(service.cancel(1L, 6L)).thenReturn(orderResponse(OrderStatus.CANCELLED));

        // WHEN
        var result = mvc.perform(user(post("/api/orders/6/cancel"))).andReturn();

        // THEN
        assertEquals(tree(orderResponse(OrderStatus.CANCELLED)), body(result, 200));
        verify(service).cancel(1L, 6L);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#invalidCheckoutRequests")
    void shouldReturnFieldErrorsWithoutCallingService_When_CheckoutInvalid(
            CreateOrderRequest input, String field, String message) throws Exception {
        // GIVEN
        String payload = json.writeValueAsString(input);

        // WHEN
        var result = mvc.perform(user(post("/api/orders")).contentType(MediaType.APPLICATION_JSON)
                .content(payload)).andReturn();

        // THEN
        fieldError(error(result, 400, "Validation failed", "/api/orders"), field, message);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#validCheckoutRequests")
    void shouldAcceptInclusiveBoundsAndOptionalComment_When_CheckoutValid(CreateOrderRequest input) throws Exception {
        // GIVEN
        when(service.create(1L, input)).thenReturn(orderResponse(OrderStatus.NEW));

        // WHEN
        var result = mvc.perform(user(post("/api/orders")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(input))).andReturn();

        // THEN
        assertEquals(tree(orderResponse(OrderStatus.NEW)), body(result, 201));
        verify(service).create(1L, input);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#malformedAuthBodies")
    void shouldRejectMalformedBody_When_CheckoutCannotBeParsed(String payload) throws Exception {
        // GIVEN
        String path = "/api/orders";

        // WHEN
        var result = mvc.perform(user(post(path)).contentType(MediaType.APPLICATION_JSON)
                .content(payload)).andReturn();

        // THEN
        assertFalse(error(result, 400, "Malformed request body", path).has("fieldErrors"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#checkoutConflicts")
    void shouldReturnConflict_When_CheckoutRejected(String message) throws Exception {
        // GIVEN
        when(service.create(eq(1L), any())).thenThrow(ApiException.conflict(message));

        // WHEN
        var result = mvc.perform(user(post("/api/orders")).contentType(MediaType.APPLICATION_JSON)
                .content(CHECKOUT_JSON)).andReturn();

        // THEN
        assertFalse(error(result, 409, message, "/api/orders").has("fieldErrors"));
        assertNull(result.getResponse().getHeader("Location"));
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#ownOrderOperations")
    void shouldReturnNotFound_When_OrderMissingOrForeign(String operation) throws Exception {
        // GIVEN
        boolean cancel = operation.equals("cancel");
        String path = cancel ? "/api/orders/6/cancel" : "/api/orders/6";
        if (cancel) when(service.cancel(1L, 6L)).thenThrow(ApiException.notFound("Order not found"));
        else when(service.get(1L, 6L)).thenThrow(ApiException.notFound("Order not found"));

        // WHEN
        var result = mvc.perform(user(cancel ? post(path) : get(path))).andReturn();

        // THEN
        assertFalse(error(result, 404, "Order not found", path).has("fieldErrors"));
    }

    @Test
    void shouldReturnConflict_When_CancellationRejected() throws Exception {
        // GIVEN
        when(service.cancel(1L, 6L)).thenThrow(ApiException.conflict("Order can no longer be cancelled"));

        // WHEN
        var result = mvc.perform(user(post("/api/orders/6/cancel"))).andReturn();

        // THEN
        error(result, 409, "Order can no longer be cancelled", "/api/orders/6/cancel");
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#customerBadParameters")
    void shouldReturnParameterFieldError_When_QueryOrIdInvalid(String url, String path, String field) throws Exception {
        // GIVEN
        var request = user(get(url));

        // WHEN
        var result = mvc.perform(request).andReturn();

        // THEN
        fieldError(error(result, 400, "Validation failed", path), field, "Invalid parameter value");
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#customerEndpoints")
    void shouldRejectAnonymousAndInvalidToken_When_AccessingOrders(String method, String path, String payload) throws Exception {
        // GIVEN
        var request = request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON).content(payload);

        // WHEN
        var anonymous = mvc.perform(request).andReturn();
        var invalid = mvc.perform(request.header("Authorization", "Bearer invalid-token")).andReturn();

        // THEN
        error(anonymous, 401, "Authentication required", path);
        error(invalid, 401, "Authentication required", path);
        verifyNoInteractions(service);
    }

    @Test
    void shouldUseAdminsOwnIdentity_When_AdminUsesCustomerEndpoints() throws Exception {
        // GIVEN
        when(service.list(9L, 0, 20)).thenReturn(orderPage());

        // WHEN
        var result = mvc.perform(admin(get("/api/orders"))).andReturn();

        // THEN
        assertEquals(tree(orderPage()), body(result, 200));
        verify(service).list(9L, 0, 20);
    }
}

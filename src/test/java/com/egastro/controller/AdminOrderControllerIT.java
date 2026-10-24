package com.egastro.controller;

import com.egastro.enums.OrderStatus;
import com.egastro.exception.ApiException;
import com.egastro.service.AdminOrderService;
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

@WebMvcTest(AdminOrderController.class)
class AdminOrderControllerIT extends WebTestSupport {
    @MockitoBean private AdminOrderService service;

    @Test
    void shouldReturnAllOrdersWithDefaults_When_FilterOmitted() throws Exception {
        // GIVEN
        when(service.list(null, 0, 20)).thenReturn(adminPage());

        // WHEN
        var result = mvc.perform(admin(get("/api/admin/orders"))).andReturn();

        // THEN
        assertEquals(tree(adminPage()), body(result, 200));
        verify(service).list(null, 0, 20);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#orderStatuses")
    void shouldBindFilterAndPagination_When_ValidParametersSupplied(OrderStatus status) throws Exception {
        // GIVEN
        when(service.list(status, 2, 5)).thenReturn(adminPage());

        // WHEN
        var result = mvc.perform(admin(get("/api/admin/orders").param("status", status.name())
                .param("page", "2").param("size", "5"))).andReturn();

        // THEN
        assertEquals(tree(adminPage()), body(result, 200));
        verify(service).list(status, 2, 5);
    }

    @Test
    void shouldReturnDetailsWithCustomerAndNoSecrets_When_OrderFound() throws Exception {
        // GIVEN
        when(service.get(6L)).thenReturn(adminResponse(OrderStatus.NEW));

        // WHEN
        var result = mvc.perform(admin(get("/api/admin/orders/6"))).andReturn();

        // THEN
        var response = body(result, 200);
        assertEquals(tree(adminResponse(OrderStatus.NEW)), response);
        assertFalse(response.has("password"));
        assertFalse(response.has("passwordHash"));
        assertFalse(response.has("token"));
    }

    @Test
    void shouldReturnUpdatedDetails_When_StatusChangeSucceeds() throws Exception {
        // GIVEN
        when(service.updateStatus(6L, statusRequest(OrderStatus.CONFIRMED)))
                .thenReturn(adminResponse(OrderStatus.CONFIRMED));

        // WHEN
        var result = mvc.perform(admin(patch("/api/admin/orders/6/status"))
                .contentType(MediaType.APPLICATION_JSON).content(CONFIRMED_JSON)).andReturn();

        // THEN
        assertEquals(tree(adminResponse(OrderStatus.CONFIRMED)), body(result, 200));
        verify(service).updateStatus(6L, statusRequest(OrderStatus.CONFIRMED));
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#invalidStatusBodies")
    void shouldReturnStatusFieldError_When_StatusMissing(String payload, String message) throws Exception {
        // GIVEN
        String path = "/api/admin/orders/6/status";

        // WHEN
        var result = mvc.perform(admin(patch(path)).contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();

        // THEN
        fieldError(error(result, 400, "Validation failed", path), "status", message);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#malformedStatusBodies")
    void shouldReturnMalformedBodyError_When_StatusCannotBeParsed(String payload) throws Exception {
        // GIVEN
        String path = "/api/admin/orders/6/status";

        // WHEN
        var result = mvc.perform(admin(patch(path)).contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();

        // THEN
        assertFalse(error(result, 400, "Malformed request body", path).has("fieldErrors"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#adminOrderOperations")
    void shouldReturnNotFound_When_OrderMissing(String operation) throws Exception {
        // GIVEN
        boolean update = operation.equals("update");
        String path = update ? "/api/admin/orders/6/status" : "/api/admin/orders/6";
        if (update) when(service.updateStatus(eq(6L), any())).thenThrow(ApiException.notFound("Order not found"));
        else when(service.get(6L)).thenThrow(ApiException.notFound("Order not found"));

        // WHEN
        var request = update ? patch(path).contentType(MediaType.APPLICATION_JSON).content(CONFIRMED_JSON) : get(path);
        var result = mvc.perform(admin(request)).andReturn();

        // THEN
        assertFalse(error(result, 404, "Order not found", path).has("fieldErrors"));
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#statusConflicts")
    void shouldReturnConflict_When_StatusChangeRejected(String message) throws Exception {
        // GIVEN
        String path = "/api/admin/orders/6/status";
        when(service.updateStatus(eq(6L), any())).thenThrow(ApiException.conflict(message));

        // WHEN
        var result = mvc.perform(admin(patch(path)).contentType(MediaType.APPLICATION_JSON).content(CONFIRMED_JSON)).andReturn();

        // THEN
        assertFalse(error(result, 409, message, path).has("fieldErrors"));
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#adminBadParameters")
    void shouldReturnParameterFieldError_When_QueryOrIdInvalid(String url, String path, String field) throws Exception {
        // GIVEN
        var request = admin(get(url));

        // WHEN
        var result = mvc.perform(request).andReturn();

        // THEN
        fieldError(error(result, 400, "Validation failed", path), field, "Invalid parameter value");
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.OrderTestData#adminEndpoints")
    void shouldRejectAnonymousInvalidTokenAndCustomer_When_AccessingAdminOrders(
            String method, String path, String payload) throws Exception {
        // GIVEN
        var request = request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON).content(payload);

        // WHEN
        var anonymous = mvc.perform(request).andReturn();
        var invalid = mvc.perform(request.header("Authorization", "Bearer invalid-token")).andReturn();
        var customer = mvc.perform(user(request(HttpMethod.valueOf(method), path))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();

        // THEN
        error(anonymous, 401, "Authentication required", path);
        error(invalid, 401, "Authentication required", path);
        error(customer, 403, "Access denied", path);
        verifyNoInteractions(service);
    }
}

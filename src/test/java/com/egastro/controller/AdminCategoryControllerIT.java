package com.egastro.controller;

import com.egastro.exception.ApiException;
import com.egastro.service.CategoryService;
import com.egastro.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.egastro.testData.RequestTestData.categoryRequest;
import static com.egastro.testData.ResponseTestData.categoryResponse;
import static com.egastro.testData.HttpTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@WebMvcTest(AdminCategoryController.class)
class AdminCategoryControllerIT extends WebTestSupport {
    @MockitoBean private CategoryService categories;

    @Test
    void shouldReturnCreatedWithLocation_When_AdminCreatesCategory() throws Exception {
        // GIVEN
        var expected = categoryResponse(2L);
        when(categories.create(categoryRequest("Dairy"))).thenReturn(expected);

        // WHEN
        var result = mvc.perform(admin(post("/api/admin/categories"))
                .contentType(MediaType.APPLICATION_JSON).content(PADDED_CATEGORY_JSON)).andReturn();

        // THEN
        assertEquals(tree(expected), body(result, 201));
        assertEquals("/api/admin/categories/2", result.getResponse().getHeader("Location"));
    }

    @Test
    void shouldReturnReplacement_When_AdminUpdatesCategory() throws Exception {
        // GIVEN
        var expected = categoryResponse(3L);
        when(categories.update(eq(3L), any())).thenReturn(expected);

        // WHEN
        var result = mvc.perform(admin(put("/api/admin/categories/3"))
                .contentType(MediaType.APPLICATION_JSON).content(CATEGORY_JSON)).andReturn();

        // THEN
        assertEquals(tree(expected), body(result, 200));
    }

    @Test
    void shouldReturnNoContent_When_AdminDeletesCategory() throws Exception {
        // GIVEN
        String path = "/api/admin/categories/3";

        // WHEN
        var result = mvc.perform(admin(delete(path))).andReturn();

        // THEN
        assertEquals(204, result.getResponse().getStatus());
        assertEquals("", result.getResponse().getContentAsString());
        verify(categories, times(1)).delete(3L);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#adminMutationMethods")
    void shouldRejectNonAdmins_When_AdminCategoryRouteAccessed(String method) throws Exception {
        // GIVEN
        String path = "/api/admin/categories" + (method.equals("POST") ? "" : "/3");
        var request = request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON)
                .content(CATEGORY_JSON);

        // WHEN
        var anonymous = mvc.perform(request).andReturn();
        var customer = mvc.perform(user(request)).andReturn();

        // THEN
        error(anonymous, 401, "Authentication required", path);
        error(customer, 403, "Access denied", path);
        verifyNoInteractions(categories);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#adminWriteMethods")
    void shouldReturnValidationErrors_When_RequiredCategoryFieldsMissing(String method) throws Exception {
        // GIVEN
        String path = "/api/admin/categories" + (method.equals("POST") ? "" : "/3");

        // WHEN
        var result = mvc.perform(admin(request(HttpMethod.valueOf(method), path))
                .contentType(MediaType.APPLICATION_JSON).content(EMPTY_JSON)).andReturn();

        // THEN
        fieldError(error(result, 400, "Validation failed", path), "name", "Name is required");
        verifyNoInteractions(categories);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#adminMutationMethods")
    void shouldReturnBusinessFailure_When_ServiceRejectsCategoryMutation(String method) throws Exception {
        // GIVEN
        String path = "/api/admin/categories" + (method.equals("POST") ? "" : "/3");
        String message = method.equals("POST") ? "Already exists" : "Resource missing";
        var failure = method.equals("POST") ? ApiException.conflict(message) : ApiException.notFound(message);
        switch (method) {
            case "POST" -> when(categories.create(any())).thenThrow(failure);
            case "PUT" -> when(categories.update(eq(3L), any())).thenThrow(failure);
            default -> doThrow(failure).when(categories).delete(3L);
        }

        // WHEN
        var result = mvc.perform(admin(request(HttpMethod.valueOf(method), path))
                .contentType(MediaType.APPLICATION_JSON).content(CATEGORY_JSON)).andReturn();

        // THEN
        error(result, method.equals("POST") ? 409 : 404, message, path);
    }

    @Test
    void shouldReturnConflict_When_DeletingNonemptyCategory() throws Exception {
        // GIVEN
        doThrow(ApiException.conflict("Category contains products and cannot be deleted")).when(categories).delete(3L);

        // WHEN
        var result = mvc.perform(admin(delete("/api/admin/categories/3"))).andReturn();

        // THEN
        error(result, 409, "Category contains products and cannot be deleted", "/api/admin/categories/3");
    }
}

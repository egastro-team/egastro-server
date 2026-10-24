package com.egastro.controller;

import com.egastro.service.CategoryService;
import com.egastro.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.DataIntegrityViolationException;

import static com.egastro.testData.ResponseTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@WebMvcTest(CategoryController.class)
class CategoryControllerIT extends WebTestSupport {
    @MockitoBean private CategoryService categories;

    @Test
    void shouldReturnCategories_When_BrowsingAnonymously() throws Exception {
        // GIVEN
        var response = categoryResponses();
        when(categories.list()).thenReturn(response);

        // WHEN
        var result = mvc.perform(get("/api/categories")).andReturn();

        // THEN
        assertEquals(tree(response), body(result, 200));
    }

    @Test
    void shouldHideInternalDetails_When_UnexpectedExceptionOccurs() throws Exception {
        // GIVEN
        when(categories.list()).thenThrow(new IllegalStateException("SQL secret internal detail"));

        // WHEN
        var result = mvc.perform(get("/api/categories")).andReturn();

        // THEN
        var response = error(result, 500, "Unexpected error", "/api/categories");
        assertFalse(response.toString().contains("secret"));
        assertFalse(response.has("fieldErrors"));
    }

    @Test
    void shouldReturnConflictWithoutSql_When_IntegrityErrorOccurs() throws Exception {
        // GIVEN
        when(categories.list()).thenThrow(new DataIntegrityViolationException("sensitive SQL"));

        // WHEN
        var result = mvc.perform(get("/api/categories")).andReturn();

        // THEN
        error(result, 409, "Data conflict", "/api/categories");
    }

    @Test
    void shouldReturnNotFound_When_AuthenticatedRouteUnknown() throws Exception {
        // GIVEN
        String path = "/api/unknown";

        // WHEN
        var result = mvc.perform(user(get(path))).andReturn();

        // THEN
        error(result, 404, "Resource not found", path);
    }

    @Test
    void shouldReturnMethodNotAllowed_When_AuthenticatedMethodUnsupported() throws Exception {
        // GIVEN
        String path = "/api/categories";

        // WHEN
        var result = mvc.perform(user(patch(path))).andReturn();

        // THEN
        error(result, 405, "Method not allowed", path);
    }

}

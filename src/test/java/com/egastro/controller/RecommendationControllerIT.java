package com.egastro.controller;

import com.egastro.service.SmartCartService;
import com.egastro.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static com.egastro.testData.RecommendationTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(RecommendationController.class)
class RecommendationControllerIT extends WebTestSupport {
    @MockitoBean private SmartCartService service;
    private static final String PATH = "/api/cart/recommendations";

    @Test
    void shouldReturnModelFields_When_Authenticated() throws Exception {
        // GIVEN
        var expected = response("MODEL");
        when(service.recommend(1L, null)).thenReturn(expected);
        // WHEN
        var result = mvc.perform(user(get(PATH))).andReturn();
        // THEN
        assertEquals(tree(expected), body(result, 200));
        verify(service).recommend(1L, null);
    }

    @Test
    void shouldReturnFallbackWithoutNullFields_When_AdminRequestsLimit() throws Exception {
        // GIVEN
        var expected = response("FALLBACK");
        when(service.recommend(eq(9L), eq(2))).thenReturn(expected);
        // WHEN
        var result = mvc.perform(admin(get(PATH).param("limit", "2"))).andReturn();
        // THEN
        var body = body(result, 200);
        assertEquals(tree(expected), body);
        assertFalse(body.path("items").get(0).has("score"));
        assertFalse(body.path("items").get(0).has("metrics"));
    }

    @Test
    void shouldRejectAccess_When_AnonymousOrInvalidToken() throws Exception {
        // GIVEN
        var request = get(PATH);
        // WHEN
        var anonymous = mvc.perform(request).andReturn();
        var invalid = mvc.perform(get(PATH).header("Authorization", "Bearer invalid-token")).andReturn();
        // THEN
        error(anonymous, 401, "Authentication required", PATH);
        error(invalid, 401, "Authentication required", PATH);
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectLimit_When_NotAnInteger() throws Exception {
        // GIVEN
        var request = user(get(PATH).param("limit", "abc"));
        // WHEN
        var result = mvc.perform(request).andReturn();
        // THEN
        fieldError(error(result, 400, "Validation failed", PATH), "limit", "Invalid parameter value");
        verifyNoInteractions(service);
    }
}

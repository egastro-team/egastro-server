package com.egastro.controller;

import com.egastro.exception.ApiException;
import com.egastro.service.AuthService;
import com.egastro.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static com.egastro.testData.RequestTestData.*;
import static com.egastro.testData.ResponseTestData.*;
import static com.egastro.testData.HttpTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@WebMvcTest(AuthController.class)
class AuthControllerIT extends WebTestSupport {
    @MockitoBean private AuthService service;

    @Test
    void shouldReturnCreatedCustomerWithoutSecrets_When_RegisteringAnonymously() throws Exception {
        // GIVEN
        var response = userResponse();
        when(service.register(registerRequest())).thenReturn(response);

        // WHEN
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER_WITH_ROLE_JSON)).andReturn();

        // THEN
        assertEquals(tree(response), body(result, 201));
        verify(service, times(1)).register(registerRequest());
    }

    @Test
    void shouldReturnBearerToken_When_LoginSucceeds() throws Exception {
        // GIVEN
        var response = loginResponse();
        when(service.login(any())).thenReturn(response);

        // WHEN
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_JSON)).andReturn();

        // THEN
        assertEquals(tree(response), body(result, 200));
    }

    @Test
    void shouldReturnMe_When_Authenticated() throws Exception {
        // GIVEN
        var response = userResponse();
        when(service.me()).thenReturn(response);

        // WHEN
        var result = mvc.perform(user(get("/api/auth/me"))).andReturn();

        // THEN
        assertEquals(tree(response), body(result, 200));
    }

    @Test
    void shouldRejectMe_When_Anonymous() throws Exception {
        // GIVEN
        String path = "/api/auth/me";

        // WHEN
        var result = mvc.perform(get(path)).andReturn();

        // THEN
        error(result, 401, "Authentication required", path);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#authFailures")
    void shouldReturnServiceError_When_AuthFails(String endpoint, int status, String message) throws Exception {
        // GIVEN
        var failure = new ApiException(HttpStatus.valueOf(status), message);
        if (endpoint.equals("register")) when(service.register(any())).thenThrow(failure);
        else when(service.login(any())).thenThrow(failure);
        String path = "/api/auth/" + endpoint;

        // WHEN
        var result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_JSON)).andReturn();

        // THEN
        assertFalse(error(result, status, message, path).has("fieldErrors"));
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#invalidAuthBodies")
    void shouldReturnFieldErrors_When_AuthBodyInvalid(String endpoint, String payload, String field, String message) throws Exception {
        // GIVEN
        String path = "/api/auth/" + endpoint;

        // WHEN
        var result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();

        // THEN
        fieldError(error(result, 400, "Validation failed", path), field, message);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#malformedAuthBodies")
    void shouldReturnMalformedBody_When_JsonMissingOrBroken(String payload) throws Exception {
        // GIVEN
        String path = "/api/auth/login";

        // WHEN
        var result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();

        // THEN
        assertFalse(error(result, 400, "Malformed request body", path).has("fieldErrors"));
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnNotFound_When_CurrentUserWasDeleted() throws Exception {
        // GIVEN
        when(service.me()).thenThrow(ApiException.notFound("User not found"));

        // WHEN
        var result = mvc.perform(user(get("/api/auth/me"))).andReturn();

        // THEN
        error(result, 404, "User not found", "/api/auth/me");
    }
}

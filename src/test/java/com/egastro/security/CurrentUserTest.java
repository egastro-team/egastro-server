package com.egastro.security;

import com.egastro.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import static com.egastro.support.TestAssertions.assertApiError;
import static com.egastro.testData.SecurityTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class CurrentUserTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnPrincipalAndId_When_AuthenticatedPrincipalPresent() {
        // GIVEN
        var principal = userPrincipal();
        SecurityContextHolder.getContext().setAuthentication(
                authentication(principal));

        // WHEN
        var user = CurrentUser.get();
        var id = CurrentUser.id();

        // THEN
        assertSame(principal, user);
        assertEquals(1L, id);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#invalidPrincipalStates")
    void shouldRejectAccess_When_NoAuthenticatedApplicationUser(String condition) {
        // GIVEN
        if (condition.equals("unauthenticated")) {
            SecurityContextHolder.getContext().setAuthentication(
                    unauthenticatedUser());
        } else if (condition.equals("wrong-principal")) {
            SecurityContextHolder.getContext().setAuthentication(
                    stringAuthentication("anonymousUser"));
        }

        // WHEN
        var error = assertThrows(ApiException.class, CurrentUser::id);

        // THEN
        assertApiError(error, HttpStatus.UNAUTHORIZED, "Authentication required");
    }
}

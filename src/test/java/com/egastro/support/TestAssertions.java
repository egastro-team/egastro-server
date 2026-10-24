package com.egastro.support;

import com.egastro.exception.ApiException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class TestAssertions {
    private TestAssertions() {
    }

    public static void assertApiError(ApiException error, HttpStatus status, String message) {
        assertAll(() -> assertEquals(status, error.getStatus()),
                () -> assertEquals(message, error.getMessage()));
    }
}

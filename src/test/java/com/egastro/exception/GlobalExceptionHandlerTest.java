package com.egastro.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.List;
import java.util.Set;

import static com.egastro.testData.ResponseTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {
    @Mock private ConstraintViolation<Object> nameViolation;
    @Mock private ConstraintViolation<Object> quantityViolation;
    @Mock private Path namePath;
    @Mock private Path quantityPath;
    @InjectMocks private GlobalExceptionHandler handler;

    @Test
    void shouldSortErrors_When_ConstraintViolationsUnordered() {
        // GIVEN
        when(nameViolation.getPropertyPath()).thenReturn(namePath);
        when(namePath.toString()).thenReturn("name");
        when(nameViolation.getMessage()).thenReturn("Name is required");
        when(quantityViolation.getPropertyPath()).thenReturn(quantityPath);
        when(quantityPath.toString()).thenReturn("quantity");
        when(quantityViolation.getMessage()).thenReturn("Quantity is required");
        var exception = new ConstraintViolationException(Set.of(quantityViolation, nameViolation));
        var request = new MockHttpServletRequest("POST", "/api/cart/items");

        // WHEN
        var response = handler.handleConstraints(exception, request);

        // THEN
        assertEquals(BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(List.of(fieldErrorItem("name", "Name is required"),
                fieldErrorItem("quantity", "Quantity is required")), response.getBody().fieldErrors());
    }

    @Test
    void shouldReturnParameterError_When_RequiredParameterMissing() {
        // GIVEN
        var request = new MockHttpServletRequest("GET", "/api/products");
        var exception = new MissingServletRequestParameterException("page", "int");

        // WHEN
        var response = handler.handleMissingParameter(exception, request);

        // THEN
        assertEquals(BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(List.of(fieldErrorItem("page", "Parameter is required")), response.getBody().fieldErrors());
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#booleanValues")
    void shouldRethrowSecurityError_When_UnexpectedHandlerReceivesIt(boolean accessDenied) {
        // GIVEN
        Exception expected = accessDenied ? new AccessDeniedException("denied") : new BadCredentialsException("invalid");
        var request = new MockHttpServletRequest("GET", "/api/cart");

        // WHEN
        var actual = assertThrows(Exception.class, () -> handler.handleUnexpected(expected, request));

        // THEN
        assertSame(expected, actual);
    }

    @Test
    void shouldDefensivelyCopyFieldErrors_When_ConstructingBusinessException() {
        // GIVEN
        var errors = new java.util.ArrayList<>(List.of(fieldErrorItem("quantity", "Invalid")));

        // WHEN
        var exception = new ApiException(BAD_REQUEST, "Validation failed", errors);
        errors.clear();

        // THEN
        assertEquals(1, exception.getFieldErrors().size());
        assertThrows(UnsupportedOperationException.class, () -> exception.getFieldErrors().clear());
    }

    @Test
    void shouldUseEmptyErrors_When_NullPassedToBusinessException() {
        // GIVEN
        List<FieldErrorItem> errors = null;

        // WHEN
        var exception = new ApiException(BAD_REQUEST, "Invalid", errors);
        var basic = ApiException.badRequest("Invalid");

        // THEN
        assertTrue(exception.getFieldErrors().isEmpty());
        assertEquals(BAD_REQUEST, basic.getStatus());
        assertEquals("Invalid", basic.getMessage());
    }
}

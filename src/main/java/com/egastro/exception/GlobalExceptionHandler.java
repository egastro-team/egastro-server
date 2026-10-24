package com.egastro.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException exception, HttpServletRequest request) {
        return respond(exception.getStatus(), exception.getMessage(), request, exception.getFieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception,
                                                         HttpServletRequest request) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorItem(error.getField(), error.getDefaultMessage()))
                .toList();
        return validation(request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraints(ConstraintViolationException exception,
                                                          HttpServletRequest request) {
        var errors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorItem(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return validation(request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException exception,
                                                           HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Malformed request body", request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                          HttpServletRequest request) {
        return validation(request, List.of(new FieldErrorItem(exception.getName(), "Invalid parameter value")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception,
                                                              HttpServletRequest request) {
        return validation(request, List.of(new FieldErrorItem(exception.getParameterName(), "Parameter is required")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException exception,
                                                       HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "Resource not found", request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception,
                                                              HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataConflict(DataIntegrityViolationException exception,
                                                          HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "Data conflict", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request)
            throws Exception {
        // Security is added in T02; match its base types without introducing the dependency here.
        for (Class<?> type = exception.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("org.springframework.security.access.AccessDeniedException")
                    || type.getName().equals("org.springframework.security.core.AuthenticationException")) {
                throw exception;
            }
        }
        log.error("Unexpected request error", exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request, List.of());
    }

    private ResponseEntity<ErrorResponse> validation(HttpServletRequest request, List<FieldErrorItem> errors) {
        var sorted = errors.stream().sorted(Comparator.comparing(FieldErrorItem::field)
                .thenComparing(FieldErrorItem::message)).toList();
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", request, sorted);
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String message, HttpServletRequest request,
                                                 List<FieldErrorItem> errors) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, request.getRequestURI(), errors));
    }
}

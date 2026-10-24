package com.egastro.testData;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

public final class TestCases {
    private TestCases() {
    }

    public static Stream<Arguments> invalidJwtTtls() {
        return Stream.of(
                Arguments.of(0),
                Arguments.of(-1));
    }

    public static Stream<Arguments> adminMutationMethods() {
        return Stream.of(
                Arguments.of("POST"),
                Arguments.of("PUT"),
                Arguments.of("DELETE"));
    }

    public static Stream<Arguments> adminWriteMethods() {
        return Stream.of(
                Arguments.of("POST"),
                Arguments.of("PUT"));
    }

    public static Stream<Arguments> malformedAuthBodies() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of("{"),
                Arguments.of("{\"email\":"),
                Arguments.of("null"));
    }

    public static Stream<Arguments> invalidAuthBodies() {
        return Stream.of(
                Arguments.of("register", "{}", "email", "Email is required"),
                Arguments.of("register", "{\"email\":\"bad\",\"password\":\"Password123\"}", "email", "Invalid email format"),
                Arguments.of("register", "{\"email\":\"user@example.com\",\"password\":\"short1\"}", "password", "Password must be at least 8 characters long"),
                Arguments.of("register", "{\"email\":\"user@example.com\",\"password\":\"abcdefgh\"}", "password", "Password must contain at least one digit"),
                Arguments.of("login", "{}", "password", "Password is required"),
                Arguments.of("login", "{\"email\":\"   \",\"password\":\"Password123\"}", "email", "Email is required"));
    }

    public static Stream<Arguments> authFailures() {
        return Stream.of(
                Arguments.of("register", "409", "A user with this email already exists"),
                Arguments.of("login", "401", "Incorrect email or password"));
    }

    public static Stream<Arguments> cartAddFailures() {
        return Stream.of(
                Arguments.of("404", "Product not found"),
                Arguments.of("409", "Product is currently unavailable"));
    }

    public static Stream<Arguments> cartMethods() {
        return Stream.of(
                Arguments.of("GET"),
                Arguments.of("POST"),
                Arguments.of("PUT"),
                Arguments.of("DELETE"));
    }

    public static Stream<Arguments> invalidCartBodies() {
        return Stream.of(
                Arguments.of("POST", "{}", "productId", "Product is required"),
                Arguments.of("POST", "{\"productId\":3}", "quantity", "Quantity is required"),
                Arguments.of("POST", "{\"productId\":null,\"quantity\":1}", "productId", "Product is required"),
                Arguments.of("POST", "{\"productId\":3,\"quantity\":0}", "quantity", "Quantity must be at least 1"),
                Arguments.of("POST", "{\"productId\":3,\"quantity\":100}", "quantity", "Maximum quantity per product is 99"),
                Arguments.of("PUT", "{}", "quantity", "Quantity is required"),
                Arguments.of("PUT", "{\"quantity\":null}", "quantity", "Quantity is required"),
                Arguments.of("PUT", "{\"quantity\":-1}", "quantity", "Quantity must be at least 1"),
                Arguments.of("PUT", "{\"quantity\":100}", "quantity", "Maximum quantity per product is 99"));
    }

    public static Stream<Arguments> cartMutations() {
        return Stream.of(
                Arguments.of("POST"),
                Arguments.of("PUT"),
                Arguments.of("DELETE"));
    }

    public static Stream<Arguments> numericCatalogParameters() {
        return Stream.of(
                Arguments.of("category"),
                Arguments.of("page"),
                Arguments.of("size"));
    }

    public static Stream<Arguments> booleanValues() {
        return Stream.of(
                Arguments.of(true),
                Arguments.of(false));
    }

    public static Stream<Arguments> invalidPrincipalStates() {
        return Stream.of(
                Arguments.of("missing"),
                Arguments.of("unauthenticated"),
                Arguments.of("wrong-principal"));
    }

    public static Stream<Arguments> missingBearerHeaders() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(""),
                Arguments.of("Basic token"),
                Arguments.of("Bearer"),
                Arguments.of("Bear token"));
    }

    public static Stream<Arguments> validBearerHeaders() {
        return Stream.of(
                Arguments.of("Bearer token"),
                Arguments.of("bearer token"),
                Arguments.of("bEaReR  token  "));
    }

    public static Stream<Arguments> invalidJwtClaims() {
        return Stream.of(
                Arguments.of("0", "user@example.com", "USER", "true"),
                Arguments.of("-1", "user@example.com", "USER", "true"),
                Arguments.of("abc", "user@example.com", "USER", "true"),
                Arguments.of(null, "user@example.com", "USER", "true"),
                Arguments.of("1", null, "USER", "true"),
                Arguments.of("1", "", "USER", "true"),
                Arguments.of("1", "   ", "USER", "true"),
                Arguments.of("1", "user@example.com", "UNKNOWN", "true"),
                Arguments.of("1", "user@example.com", null, "true"),
                Arguments.of("1", "user@example.com", "USER", "false"));
    }

    public static Stream<Arguments> malformedTokens() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(""),
                Arguments.of(" "),
                Arguments.of("not-a-jwt"),
                Arguments.of("a.b.c"));
    }

    public static Stream<Arguments> invalidLoginPasswords() {
        return Stream.of(
                Arguments.of("wrong-password"),
                Arguments.of("too-long"));
    }

    public static Stream<Arguments> quantityBoundaries() {
        return Stream.of(
                Arguments.of(1),
                Arguments.of(99));
    }

    public static Stream<Arguments> invalidQuantities() {
        return Stream.of(
                Arguments.of(null, "Quantity is required"),
                Arguments.of("0", "Quantity must be at least 1"),
                Arguments.of("-1", "Quantity must be at least 1"),
                Arguments.of("100", "Maximum quantity per product is 99"));
    }

    public static Stream<Arguments> productOperations() {
        return Stream.of(
                Arguments.of("get"),
                Arguments.of("update"),
                Arguments.of("delete"));
    }

    public static Stream<Arguments> catalogSearches() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(""),
                Arguments.of(" Milk "),
                Arguments.of("%_\\"),
                Arguments.of("x"));
    }

    public static Stream<Arguments> unsupportedSortValues() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of("NAME_ASC"),
                Arguments.of("unknown"),
                Arguments.of(" name_asc "));
    }

    public static Stream<Arguments> productSorts() {
        return java.util.Arrays.stream(com.egastro.enums.ProductSort.values()).map(Arguments::of);
    }

    public static Stream<Arguments> paginationBounds() {
        return Stream.of(
                Arguments.of("-1", "-1", "0", "1"),
                Arguments.of("0", "0", "0", "1"),
                Arguments.of("2", "1", "2", "1"),
                Arguments.of("2", "100", "2", "100"),
                Arguments.of("0", "101", "0", "100"),
                Arguments.of("3", "20", "3", "20"));
    }

}

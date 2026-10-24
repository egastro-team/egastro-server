package com.egastro.testData;

import com.egastro.dto.request.*;
import com.egastro.enums.UnitOfMeasure;

import java.math.BigDecimal;

public final class RequestTestData {
    private RequestTestData() {
    }

    public static RegisterRequest registerRequest(String email, String password) {
        return new RegisterRequest(email, password);
    }

    public static RegisterRequest registerRequest() {
        return registerRequest("user@example.com", "Password123");
    }

    public static RegisterRequest paddedRegisterRequest() {
        return registerRequest(" User@Example.COM ", " Password123 ");
    }

    public static RegisterRequest longUtf8RegisterRequest() {
        return registerRequest("user@example.com", "я".repeat(36) + "1");
    }

    public static LoginRequest loginRequest(String email, String password) {
        return new LoginRequest(email, password);
    }

    public static LoginRequest paddedLoginRequest() {
        return loginRequest(" user@example.com ", "Password123");
    }

    public static LoginRequest missingUserLoginRequest() {
        return loginRequest("missing@example.com", "Password123");
    }

    public static LoginRequest wrongPasswordLoginRequest(String variant) {
        return loginRequest("user@example.com", variant.equals("too-long") ? "x".repeat(73) : variant);
    }

    public static CategoryRequest categoryRequest(String name) {
        return new CategoryRequest(name);
    }

    public static AddCartItemRequest addCartItemRequest(Long productId, Integer quantity) {
        return new AddCartItemRequest(productId, quantity);
    }

    public static UpdateCartItemRequest updateCartItemRequest(Integer quantity) {
        return new UpdateCartItemRequest(quantity);
    }

    public static ProductRequest replacementProductRequest() {
        return new ProductRequest(" Cheese ", " Description ", 2L, new BigDecimal("12.3"),
                UnitOfMeasure.KG, " ", false);
    }
}

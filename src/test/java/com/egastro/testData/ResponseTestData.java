package com.egastro.testData;

import com.egastro.dto.response.*;
import com.egastro.enums.UnitOfMeasure;
import com.egastro.exception.FieldErrorItem;
import com.egastro.service.CartService;
import com.egastro.entity.Product;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

public final class ResponseTestData {
    private ResponseTestData() {
    }

    public static UserResponse userResponse() {
        return new UserResponse(1L, "user@example.com", "USER");
    }

    public static LoginResponse loginResponse() {
        return new LoginResponse("test-access-token", "Bearer", 86400, userResponse());
    }

    public static LoginResponse loginResponse(String token) {
        return new LoginResponse(token, "Bearer", 3600, null);
    }

    public static CategoryResponse categoryResponse(Long id) {
        return new CategoryResponse(id, "Dairy");
    }

    public static List<CategoryResponse> categoryResponses() {
        return List.of(categoryResponse(2L));
    }

    public static ProductResponse productResponse() {
        return new ProductResponse(3L, "Milk", "Fresh milk", new BigDecimal("2.49"), UnitOfMeasure.L,
                "https://example.com/milk.png", true, 2L, "Dairy");
    }

    public static CartResponse emptyCartResponse() {
        return new CartResponse(List.of(), 0, new BigDecimal("0.00"));
    }

    public static CartResponse cartResponse() {
        return new CartResponse(List.of(new CartItemResponse(3L, "Milk", "https://example.com/milk.png",
                new BigDecimal("2.49"), UnitOfMeasure.L, 2, new BigDecimal("4.98"), true)),
                2, new BigDecimal("4.98"));
    }

    public static PageResponse<ProductResponse> productPageResponse() {
        return new PageResponse<>(List.of(productResponse()), 0, 20, 1, 1);
    }

    public static PageResponse<ProductResponse> emptyProductPageResponse() {
        return new PageResponse<>(List.of(), 2, 5, 0, 0);
    }

    public static PageImpl<Product> productPage(Pageable pageable) {
        return new PageImpl<>(List.of(TestFixtures.product()), pageable, 1);
    }

    public static PageImpl<Product> emptyProductPage(Pageable pageable) {
        return new PageImpl<>(List.of(), pageable, 0);
    }

    public static PageImpl<Product> emptyProductPage() {
        return new PageImpl<>(List.of());
    }

    public static PageImpl<String> textPage() {
        return new PageImpl<>(List.of("Milk", "Bread"), PageRequest.of(1, 2, Sort.by("id")), 7);
    }

    public static PageResponse<String> textPageResponse() {
        return new PageResponse<>(List.of("Milk", "Bread"), 1, 2, 7, 4);
    }

    public static FieldErrorItem fieldErrorItem(String field, String message) {
        return new FieldErrorItem(field, message);
    }

    public static CartService.CartProduct cartProduct(Product product, int quantity) {
        return new CartService.CartProduct(product, quantity);
    }
}

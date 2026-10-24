package com.egastro.controller;

import com.egastro.dto.request.AddCartItemRequest;
import com.egastro.dto.request.UpdateCartItemRequest;
import com.egastro.dto.response.CartResponse;
import com.egastro.security.CurrentUser;
import com.egastro.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Shopping cart")
public class CartController {
    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get the current user's cart")
    public CartResponse get() {
        return cartService.get(CurrentUser.id());
    }

    @PostMapping("/items")
    @Operation(summary = "Add a product to the cart")
    public CartResponse addItem(@Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(CurrentUser.id(), request);
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Set a cart item's quantity")
    public CartResponse updateItem(@PathVariable Long productId,
                                   @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(CurrentUser.id(), productId, request);
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove a product from the cart")
    public CartResponse removeItem(@PathVariable Long productId) {
        return cartService.removeItem(CurrentUser.id(), productId);
    }
}

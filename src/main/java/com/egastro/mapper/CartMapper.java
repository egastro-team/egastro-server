package com.egastro.mapper;

import com.egastro.dto.response.CartItemResponse;
import com.egastro.dto.response.CartResponse;
import com.egastro.entity.CartItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class CartMapper {
    public CartResponse toResponse(List<CartItem> items) {
        List<CartItemResponse> responses = items.stream().map(this::toItemResponse).toList();
        int totalQuantity = responses.stream().mapToInt(CartItemResponse::quantity).sum();
        BigDecimal totalAmount = responses.stream().map(CartItemResponse::lineTotal)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
        return new CartResponse(responses, totalQuantity, totalAmount);
    }

    private CartItemResponse toItemResponse(CartItem item) {
        var product = item.getProduct();
        BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemResponse(product.getId(), product.getName(), product.getImageUrl(),
                product.getPrice(), product.getUnitOfMeasure(), item.getQuantity(), lineTotal,
                product.isAvailable());
    }
}

package com.egastro.dto.response;

import com.egastro.enums.UnitOfMeasure;

import java.math.BigDecimal;

public record CartItemResponse(Long productId, String name, String imageUrl, BigDecimal unitPrice,
                               UnitOfMeasure unitOfMeasure, int quantity, BigDecimal lineTotal,
                               boolean available) {
}

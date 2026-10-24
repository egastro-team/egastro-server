package com.egastro.dto.response;

import com.egastro.enums.UnitOfMeasure;

import java.math.BigDecimal;

public record ProductResponse(Long id, String name, String description, BigDecimal price,
                              UnitOfMeasure unitOfMeasure, String imageUrl, boolean available,
                              Long categoryId, String categoryName) {
}

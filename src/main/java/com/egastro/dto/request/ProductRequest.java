package com.egastro.dto.request;

import com.egastro.enums.UnitOfMeasure;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters long") String name,
        @Size(max = 5000, message = "Description must be at most 5000 characters long") String description,
        @NotNull(message = "Category is required") Long categoryId,
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0", inclusive = false, message = "Price must be greater than 0")
        @DecimalMax(value = "9999999999.99", message = "Price must be at most 9999999999.99")
        @Digits(integer = 10, fraction = 2, message = "Price must have at most 10 integer digits and 2 decimals")
        BigDecimal price,
        @NotNull(message = "Unit of measure is required") UnitOfMeasure unitOfMeasure,
        @Size(max = 1000, message = "Image URL must be at most 1000 characters long")
        @URL(regexp = "https?://[^/\\s?#]+(?:[/?#][^\\s]*)?",
                message = "Image URL must be an absolute HTTP or HTTPS URL")
        String imageUrl,
        Boolean available
) {
    public ProductRequest {
        name = name == null ? null : name.trim();
        description = description == null ? null : description.trim();
        imageUrl = imageUrl == null ? null : imageUrl.trim();
        if (imageUrl != null && imageUrl.isEmpty()) {
            imageUrl = null;
        }
        available = available == null ? Boolean.TRUE : available;
    }
}

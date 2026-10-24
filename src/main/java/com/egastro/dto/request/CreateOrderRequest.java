package com.egastro.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotBlank(message = "Delivery address is required")
        @Size(max = 500, message = "Delivery address must be at most 500 characters long") String deliveryAddress,
        @NotBlank(message = "Invalid phone number")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number") String contactPhone,
        @Size(max = 1000, message = "Comment must be at most 1000 characters long") String comment
) {
    public CreateOrderRequest {
        deliveryAddress = deliveryAddress == null ? null : deliveryAddress.trim();
        contactPhone = contactPhone == null ? null : contactPhone.replaceAll("[ ()-]", "");
        comment = comment == null ? null : comment.trim();
    }
}

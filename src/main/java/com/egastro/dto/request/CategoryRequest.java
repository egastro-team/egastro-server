package com.egastro.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters long") String name
) {
    public CategoryRequest {
        name = name == null ? null : name.trim();
    }
}

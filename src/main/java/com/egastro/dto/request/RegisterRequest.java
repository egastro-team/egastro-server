package com.egastro.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Size(max = 254, message = "Email must be at most 254 characters long")
        @Email(message = "Invalid email format") String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        @Size(max = 72, message = "Password must be at most 72 characters long")
        @Pattern(regexp = "(?s).*[0-9].*", message = "Password must contain at least one digit") String password
) {
    public RegisterRequest {
        email = email == null ? null : email.trim();
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=[REDACTED]]";
    }
}

package com.egastro.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank(message = "Email is required") String email,
                           @NotBlank(message = "Password is required") String password) {
    public LoginRequest {
        email = email == null ? null : email.trim();
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=[REDACTED]]";
    }
}

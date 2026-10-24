package com.egastro.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("egastro.security.jwt")
public record JwtProperties(@NotBlank String secret, @NotNull Duration ttl) {
    public JwtProperties {
        if (ttl != null && (ttl.isNegative() || ttl.isZero())) {
            throw new IllegalArgumentException("JWT ttl must be positive");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=[REDACTED], ttl=" + ttl + "]";
    }
}

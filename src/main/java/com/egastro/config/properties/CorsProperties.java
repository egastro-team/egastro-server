package com.egastro.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("egastro.security.cors")
public record CorsProperties(List<String> allowedOrigins) {
}

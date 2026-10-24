package com.egastro.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties("egastro.recommendations")
public record RecommendationProperties(Ml ml, int defaultLimit, int maxLimit) {
    public RecommendationProperties {
        if (defaultLimit < 1 || maxLimit < defaultLimit) {
            throw new IllegalArgumentException("Recommendation limits must satisfy 1 <= default-limit <= max-limit");
        }
    }

    public record Ml(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        public Ml {
            if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()
                    || readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
                throw new IllegalArgumentException("Recommendation timeouts must be positive");
            }
        }
    }
}

package com.egastro.config.properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.time.Duration;
import static com.egastro.testData.RecommendationTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class RecommendationPropertiesTest {
    @ParameterizedTest
    @MethodSource("com.egastro.testData.RecommendationTestData#invalidLimits")
    void shouldRejectConfig_When_LimitsAreInvalid(int defaultLimit, int maxLimit) {
        // GIVEN
        var ml = properties().ml();
        // WHEN
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new RecommendationProperties(ml, defaultLimit, maxLimit));
        // THEN
        assertEquals("Recommendation limits must satisfy 1 <= default-limit <= max-limit", exception.getMessage());
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.RecommendationTestData#invalidTimeouts")
    void shouldRejectConfig_When_TimeoutsAreMissingOrNonPositive(Duration connect, Duration read) {
        // GIVEN
        String url = properties().ml().baseUrl();
        // WHEN
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new RecommendationProperties.Ml(url, connect, read));
        // THEN
        assertEquals("Recommendation timeouts must be positive", exception.getMessage());
    }

    @Test
    void shouldAllowMatchingLimits_When_Positive() {
        // GIVEN
        var ml = properties().ml();
        // WHEN
        var result = new RecommendationProperties(ml, 1, 1);
        // THEN
        assertEquals(1, result.defaultLimit());
        assertEquals(1, result.maxLimit());
    }
}

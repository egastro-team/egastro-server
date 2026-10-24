package com.egastro.config.properties;

import com.egastro.dto.request.LoginRequest;
import com.egastro.dto.request.RegisterRequest;
import com.egastro.dto.response.LoginResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;

import static com.egastro.testData.TestFixtures.userWithPasswordHash;
import static com.egastro.testData.RequestTestData.*;
import static com.egastro.testData.ResponseTestData.*;
import static com.egastro.testData.SecurityTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class PropertiesAndSecretsTest {
    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#invalidJwtTtls")
    void shouldRejectTtl_When_NotPositive(long seconds) {
        // GIVEN
        var ttl = Duration.ofSeconds(seconds);

        // WHEN
        var error = assertThrows(IllegalArgumentException.class, () -> jwtProperties("secret", ttl));

        // THEN
        assertEquals("JWT ttl must be positive", error.getMessage());
    }

    @Test
    void shouldAllowNullForBeanValidation_When_OptionalConstructorInputsNull() {
        // GIVEN
        String missing = null;

        // WHEN
        var admin = adminProperties(missing, missing);
        var jwt = jwtProperties(missing, null);

        // THEN
        assertNull(admin.email());
        assertNull(jwt.ttl());
    }

    @Test
    void shouldRedactSecrets_When_ObjectsConvertedToString() {
        // GIVEN
        String secret = REDACTED_SECRET;
        var user = userWithPasswordHash(secret);

        // WHEN
        var representations = new String[] {
                loginRequest("user@example.com", secret).toString(),
                registerRequest("user@example.com", secret).toString(),
                loginResponse(secret).toString(),
                adminProperties("admin@example.com", secret).toString(),
                jwtProperties(secret, Duration.ofHours(1)).toString(), user.toString()
        };

        // THEN
        for (String representation : representations) assertFalse(representation.contains(secret));
    }
}

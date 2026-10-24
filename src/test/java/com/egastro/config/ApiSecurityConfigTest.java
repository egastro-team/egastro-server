package com.egastro.config;

import com.egastro.security.JwtAuthenticationFilter;
import com.egastro.security.JwtService;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ApiSecurityConfigTest {
    @Mock private JwtService jwtService;

    @Test
    void shouldAdvertiseBearerJwt_When_OpenApiBuilt() {
        // GIVEN
        var config = new OpenApiConfig();

        // WHEN
        var api = config.openAPI();

        // THEN
        assertEquals("eGastro API", api.getInfo().getTitle());
        assertEquals("1.0", api.getInfo().getVersion());
        var scheme = api.getComponents().getSecuritySchemes().get("bearerAuth");
        assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
        assertEquals("bearer", scheme.getScheme());
        assertEquals("JWT", scheme.getBearerFormat());
        assertTrue(api.getSecurity().getFirst().containsKey("bearerAuth"));
    }

    @Test
    void shouldUseBcrypt_When_PasswordEncoderCreated() {
        // GIVEN
        var config = new SecurityConfig();

        // WHEN
        var encoder = config.passwordEncoder();
        var encoded = encoder.encode("TestPassword123");

        // THEN
        assertInstanceOf(BCryptPasswordEncoder.class, encoder);
        assertNotEquals("TestPassword123", encoded);
        assertTrue(encoder.matches("TestPassword123", encoded));
        assertFalse(encoder.matches("OtherPassword123", encoded));
    }

    @Test
    void shouldDisableServletRegistration_When_JwtFilterRegisteredInSecurityChain() {
        // GIVEN
        var config = new SecurityConfig();
        var filter = new JwtAuthenticationFilter(jwtService);

        // WHEN
        var registration = config.jwtFilterRegistration(filter);

        // THEN
        assertSame(filter, registration.getFilter());
        assertFalse(registration.isEnabled());
    }
}

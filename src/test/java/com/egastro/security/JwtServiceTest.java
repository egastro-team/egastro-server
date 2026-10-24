package com.egastro.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;

import static com.egastro.testData.TestFixtures.user;
import static com.egastro.testData.SecurityTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private final SecretKey key = signingKey();
    private final JwtService service = new JwtService(signingJwtProperties());

    @Test
    void shouldRoundTripClaimsAndExpiry_When_TokenGenerated() {
        // GIVEN
        var user = user();
        var before = Instant.now().minusSeconds(1);

        // WHEN
        String token = service.generateToken(user);
        var parsed = service.parse(token).orElseThrow();
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        // THEN
        assertEquals(userPrincipal(), parsed);
        assertTrue(claims.getIssuedAt().toInstant().isAfter(before));
        assertEquals(3600, Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()).toSeconds());
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#malformedTokens")
    void shouldReturnEmpty_When_TokenMalformed(String token) {
        // GIVEN
        var input = token;

        // WHEN
        var result = service.parse(input);

        // THEN
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmpty_When_SignatureUsesDifferentKey() {
        // GIVEN
        var other = new JwtService(otherJwtProperties());
        String token = other.generateToken(user());

        // WHEN
        var result = service.parse(token);

        // THEN
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldRejectToken_When_ExpiredWithoutSleeping() {
        // GIVEN
        String token = expiredToken();

        // WHEN
        var result = service.parse(token);

        // THEN
        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#invalidJwtClaims")
    void shouldReturnEmpty_When_RequiredClaimsInvalid(String subject, String email, String role, boolean expires) {
        // GIVEN
        String token = tokenWithClaims(subject, email, role, expires);

        // WHEN
        var result = service.parse(token);

        // THEN
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldRejectToken_When_SignedWithOtherAlgorithm() {
        // GIVEN
        String token = tokenWithOtherAlgorithm();

        // WHEN
        var result = service.parse(token);

        // THEN
        assertTrue(result.isEmpty());
    }
}

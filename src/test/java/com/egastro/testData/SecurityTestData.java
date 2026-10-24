package com.egastro.testData;

import com.egastro.config.properties.AdminProperties;
import com.egastro.config.properties.JwtProperties;
import com.egastro.enums.Role;
import com.egastro.security.AuthenticatedUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class SecurityTestData {
    public static final String SIGNING_SECRET = "test-only-signing-key-with-at-least-sixty-four-bytes-1234567890abcdef";
    public static final String REDACTED_SECRET = "test-secret-never-print";
    public static final String USER_TOKEN = "user-token";
    public static final String ADMIN_TOKEN = "admin-token";

    private SecurityTestData() {
    }

    public static AuthenticatedUser userPrincipal() {
        return new AuthenticatedUser(1L, "user@example.com", Role.USER);
    }

    public static AuthenticatedUser adminPrincipal() {
        return new AuthenticatedUser(9L, "admin@example.com", Role.ADMIN);
    }

    public static UsernamePasswordAuthenticationToken userAuthentication() {
        return authentication(userPrincipal());
    }

    public static UsernamePasswordAuthenticationToken authentication(AuthenticatedUser principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    public static UsernamePasswordAuthenticationToken unauthenticatedUser() {
        return new UsernamePasswordAuthenticationToken(userPrincipal(), null);
    }

    public static UsernamePasswordAuthenticationToken stringAuthentication(String principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    public static JwtProperties authJwtProperties() {
        return jwtProperties("test-secret-32-bytes-or-longer-12345", Duration.ofHours(24));
    }

    public static JwtProperties signingJwtProperties() {
        return jwtProperties(SIGNING_SECRET, Duration.ofHours(1));
    }

    public static JwtProperties otherJwtProperties() {
        return jwtProperties("different-test-secret-at-least-32-bytes", Duration.ofHours(1));
    }

    public static JwtProperties jwtProperties(String secret, Duration ttl) {
        return new JwtProperties(secret, ttl);
    }

    public static AdminProperties adminProperties() {
        return adminProperties(" Admin@Example.COM ", "TestAdmin123");
    }

    public static AdminProperties adminProperties(String email, String password) {
        return new AdminProperties(email, password);
    }

    public static SecretKey signingKey() {
        return Keys.hmacShaKeyFor(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    public static String expiredToken() {
        return Jwts.builder().subject("1").claim("email", "user@example.com").claim("role", "USER")
                .expiration(Date.from(Instant.now().minusSeconds(60))).signWith(signingKey(), Jwts.SIG.HS256).compact();
    }

    public static String tokenWithClaims(String subject, String email, String role, boolean expires) {
        var builder = Jwts.builder().subject(subject).claim("email", email).claim("role", role);
        if (expires) builder.expiration(Date.from(Instant.now().plusSeconds(3600)));
        return builder.signWith(signingKey(), Jwts.SIG.HS256).compact();
    }

    public static String tokenWithOtherAlgorithm() {
        return Jwts.builder().claims(Map.of("sub", "1", "email", "user@example.com", "role", "USER"))
                .expiration(Date.from(Instant.now().plusSeconds(3600))).signWith(signingKey(), Jwts.SIG.HS384).compact();
    }
}

package com.egastro.security;

import com.egastro.config.properties.JwtProperties;
import com.egastro.entity.User;
import com.egastro.enums.Role;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {
    private final SecretKey key;
    private final JwtParser parser;
    private final Duration ttl;

    public JwtService(JwtProperties properties) {
        key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        parser = Jwts.parser().verifyWith(key).build();
        ttl = properties.ttl();
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<AuthenticatedUser> parse(String token) {
        try {
            var signedClaims = parser.parseSignedClaims(token);
            if (!Jwts.SIG.HS256.getId().equals(signedClaims.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            var claims = signedClaims.getPayload();
            Long id = Long.valueOf(claims.getSubject());
            String email = claims.get("email", String.class);
            Role role = Role.valueOf(claims.get("role", String.class));
            if (id <= 0 || email == null || email.isBlank() || claims.getExpiration() == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(id, email, role));
        } catch (RuntimeException exception) {
            // Invalid input, signatures, claims and expired tokens all mean anonymous access.
            return Optional.empty();
        }
    }
}

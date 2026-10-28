package com.enterprise.rag.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.enterprise.rag.config.RagProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final String ROLES_CLAIM = "roles";
    private static final String USER_ID_CLAIM = "uid";

    private final SecretKey key;
    private final Duration expiration;

    @Autowired
    public JwtService(RagProperties properties) {
        this(properties.jwt().secret(), Duration.ofMinutes(properties.jwt().expirationMinutes()));
    }

    public JwtService(String secret, Duration expiration) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("rag.jwt.secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiration = expiration;
    }

    public String generateToken(AuthUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.username())
                .claim(USER_ID_CLAIM, user.id())
                .claim(ROLES_CLAIM, user.roles().stream().map(Role::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /**
     * Validates signature and expiry and rebuilds the principal.
     *
     * @throws JwtException if the token is invalid or expired
     */
    public AuthUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        List<?> rawRoles = claims.get(ROLES_CLAIM, List.class);
        Set<Role> roles = rawRoles == null ? Set.of()
                : rawRoles.stream().map(Object::toString).map(Role::valueOf).collect(Collectors.toSet());
        Number uid = claims.get(USER_ID_CLAIM, Number.class);
        return new AuthUser(uid == null ? null : uid.longValue(), claims.getSubject(), roles);
    }

    public Duration expiration() {
        return expiration;
    }
}

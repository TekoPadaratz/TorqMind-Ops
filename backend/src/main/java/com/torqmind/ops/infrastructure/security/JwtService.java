package com.torqmind.ops.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final long expirationMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.expiration-minutes:720}") long expirationMinutes
    ) {
        String normalizedSecret = secret == null ? "" : secret.trim();
        String lowerSecret = normalizedSecret.toLowerCase();
        if (normalizedSecret.length() < 32
                || lowerSecret.startsWith("change-me")
                || lowerSecret.startsWith("trocar-")) {
            throw new IllegalStateException(
                    "JWT_SECRET deve ser aleatório, ter ao menos 32 caracteres e não usar o valor de exemplo.");
        }
        this.key = Keys.hmacShaKeyFor(sha256(normalizedSecret));
        this.issuer = issuer;
        this.expirationMinutes = expirationMinutes;
    }

    public String generate(UUID userId, String username, String role, Long companyId, Long branchId) {
        Instant now = Instant.now();
        Instant exp = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        var builder = Jwts.builder()
                .issuer(issuer)
                .subject(username)
                .claim("uid", userId.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key);
        if (companyId != null) {
            builder.claim("cid", companyId);
        }
        if (branchId != null) {
            builder.claim("bid", branchId);
        }
        return builder.compact();
    }

    public AppUserPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        UUID uid = UUID.fromString(claims.get("uid", String.class));
        String role = claims.get("role", String.class);
        Long companyId = claimLong(claims, "cid");
        Long branchId = claimLong(claims, "bid");
        return new AppUserPrincipal(uid, claims.getSubject(), role, companyId, branchId);
    }

    private static Long claimLong(Claims claims, String name) {
        Object v = claims.get(name);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel", ex);
        }
    }
}

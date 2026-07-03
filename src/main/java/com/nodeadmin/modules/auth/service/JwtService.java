package com.nodeadmin.modules.auth.service;

import com.nodeadmin.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JJWT 0.12.x implementation of {@link IJwtService}.
 *
 * <p>Token shape (claims):
 * <ul>
 *   <li>{@code jti}       — unique token ID (UUID) — guarantees uniqueness even
 *                           for same-second logins with identical claims</li>
 *   <li>{@code sub}       — user ID (UUID string)</li>
 *   <li>{@code email}     — user email</li>
 *   <li>{@code guardName} — {@code "web"} or {@code "api"}</li>
 *   <li>{@code iat}       — issued-at</li>
 *   <li>{@code exp}       — expiry (iat + app.jwt.expiration seconds)</li>
 * </ul>
 *
 * <p>Blacklist: Redis key {@code jwt:blacklist:{token}}, TTL = remaining token lifetime.
 * Using {@link StringRedisTemplate} with {@code expire()} so the key self-expires —
 * matching NodeAdmin's pattern (clientRedis.v4.set with EX option).
 */
@Service
public class JwtService implements IJwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_EMAIL      = "email";
    private static final String CLAIM_GUARD_NAME = "guardName";
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final AppProperties       appProperties;
    private final StringRedisTemplate redisTemplate;

    public JwtService(AppProperties appProperties, StringRedisTemplate redisTemplate) {
        this.appProperties = appProperties;
        this.redisTemplate = redisTemplate;
    }

    // -------------------------------------------------------------------------
    // IJwtService
    // -------------------------------------------------------------------------

    @Override
    public String generateToken(String userId, String email, String guardName) {
        long nowMs  = System.currentTimeMillis();
        long expMs  = nowMs + appProperties.getJwt().getExpirationSeconds() * 1_000L;

        return Jwts.builder()
                // jti unik per token — tanpa ini, dua login dengan klaim sama pada
                // detik yang sama (iat/exp resolusi detik) menghasilkan token
                // byte-identik, sehingga blacklist logout token lama ikut
                // mematikan token baru.
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_GUARD_NAME, guardName)
                .issuedAt(new Date(nowMs))
                .expiration(new Date(expMs))
                .signWith(secretKey())
                .compact();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            if (isBlacklisted(token)) {
                return false;
            }
            parseClaims(token); // throws on invalid / expired
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    @Override
    public String extractGuardName(String token) {
        return parseClaims(token).get(CLAIM_GUARD_NAME, String.class);
    }

    @Override
    public void blacklistToken(String token, long expirationMs) {
        if (expirationMs <= 0) {
            return; // token already expired — nothing to blacklist
        }
        String key = BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "1");
        redisTemplate.expire(key, expirationMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Builds the signing key from the secret in AppProperties.
     *  Uses BASE64URL decoder — the default secret contains hyphens which are
     *  valid in Base64URL but illegal in standard Base64. */
    private SecretKey secretKey() {
        byte[] keyBytes = Decoders.BASE64URL.decode(appProperties.getJwt().getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Parses and verifies the JWT, returning its claims.
     * Throws {@link JwtException} on any verification failure.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Returns the remaining lifetime of a token in milliseconds.
     * Returns 0 if the token cannot be parsed or is already expired.
     */
    public long remainingMs(String token) {
        try {
            Date exp = parseClaims(token).getExpiration();
            long remaining = exp.getTime() - System.currentTimeMillis();
            return Math.max(0L, remaining);
        } catch (JwtException | IllegalArgumentException e) {
            return 0L;
        }
    }
}

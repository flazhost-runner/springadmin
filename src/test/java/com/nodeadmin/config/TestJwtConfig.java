package com.nodeadmin.config;

import com.nodeadmin.modules.auth.service.IJwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-profile replacement for {@link com.nodeadmin.modules.auth.service.JwtService}.
 *
 * <p>Substitutes the Redis-backed JWT blacklist with an in-memory
 * {@link ConcurrentHashMap} so tests run without a Redis server.
 * Marked {@code @Primary} so Spring picks this bean over the production
 * {@code JwtService} when the "test" profile is active.
 */
@TestConfiguration
@Profile("test")
public class TestJwtConfig {

    @Bean
    @Primary
    public IJwtService testJwtService(AppProperties appProperties) {
        return new InMemoryJwtService(appProperties);
    }

    // -------------------------------------------------------------------------
    // Inner implementation
    // -------------------------------------------------------------------------

    static class InMemoryJwtService implements IJwtService {

        private static final String CLAIM_EMAIL      = "email";
        private static final String CLAIM_GUARD_NAME = "guardName";

        private final AppProperties appProperties;

        /** token → expiry timestamp (ms). Null expiry = never expires in tests. */
        private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

        InMemoryJwtService(AppProperties appProperties) {
            this.appProperties = appProperties;
        }

        @Override
        public String generateToken(String userId, String email, String guardName) {
            long nowMs = System.currentTimeMillis();
            long expMs = nowMs + appProperties.getJwt().getExpirationSeconds() * 1_000L;
            return Jwts.builder()
                    // jti unik — mencegah token byte-identik saat dua login terjadi
                    // pada detik yang sama (mirror perbaikan di JwtService produksi).
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
                if (isBlacklisted(token)) return false;
                parseClaims(token);
                return true;
            } catch (JwtException | IllegalArgumentException e) {
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
            if (expirationMs > 0) {
                blacklist.put(token, System.currentTimeMillis() + expirationMs);
            }
        }

        @Override
        public boolean isBlacklisted(String token) {
            Long expiresAt = blacklist.get(token);
            if (expiresAt == null) return false;
            // Auto-evict expired entries
            if (System.currentTimeMillis() > expiresAt) {
                blacklist.remove(token);
                return false;
            }
            return true;
        }

        // Remaining lifetime helper (mirrors JwtService.remainingMs)
        public long remainingMs(String token) {
            try {
                Date exp = parseClaims(token).getExpiration();
                return Math.max(0L, exp.getTime() - System.currentTimeMillis());
            } catch (JwtException | IllegalArgumentException e) {
                return 0L;
            }
        }

        private SecretKey secretKey() {
            byte[] keyBytes = Decoders.BASE64.decode(appProperties.getJwt().getSecret());
            return Keys.hmacShaKeyFor(keyBytes);
        }

        private Claims parseClaims(String token) {
            return Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }
    }
}

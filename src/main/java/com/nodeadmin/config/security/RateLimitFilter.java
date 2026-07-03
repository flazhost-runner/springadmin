package com.nodeadmin.config.security;

import com.nodeadmin.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiter for sensitive auth endpoints using Bucket4j.
 *
 * <p>Two bucket pools:
 * <ul>
 *   <li><b>auth</b>  — login + register: configured via {@code app.rate-limit.auth.*}</li>
 *   <li><b>otp</b>   — reset-request + reset-process: {@code app.rate-limit.otp.*}</li>
 * </ul>
 *
 * <p>Only POST requests to the listed paths are counted. GET and other methods
 * pass through unconditionally. IP is resolved from {@code X-Forwarded-For}
 * (first hop) then {@code RemoteAddr}.
 *
 * <p>When a bucket is exhausted the filter writes a 429 JSON body and short-circuits
 * the filter chain — no further processing occurs.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> AUTH_PATHS = Set.of(
            "/auth/login",             "/api/v1/auth/login",
            "/auth/register",          "/api/v1/auth/register"
    );

    private static final Set<String> OTP_PATHS = Set.of(
            "/auth/reset/request",     "/api/v1/auth/reset/request",
            "/auth/reset/process",     "/api/v1/auth/reset/process"
    );

    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> otpBuckets  = new ConcurrentHashMap<>();

    private final AppProperties.RateLimit.BucketConfig authConfig;
    private final AppProperties.RateLimit.BucketConfig otpConfig;

    public RateLimitFilter(AppProperties appProperties) {
        this.authConfig = appProperties.getRateLimit().getAuth();
        this.otpConfig  = appProperties.getRateLimit().getOtp();
    }

    private static final Set<String> LOOPBACK_IPS = Set.of(
            "127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "localhost"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getServletPath();
        String ip   = resolveClientIp(request);

        if (LOOPBACK_IPS.contains(ip)) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = null;
        if (AUTH_PATHS.contains(path)) {
            bucket = authBuckets.computeIfAbsent(ip, k -> newBucket(authConfig));
        } else if (OTP_PATHS.contains(path)) {
            bucket = otpBuckets.computeIfAbsent(ip, k -> newBucket(otpConfig));
        }

        if (bucket != null && !bucket.tryConsume(1)) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"status\":\"error\",\"message\":\"Too many requests. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Bucket newBucket(AppProperties.RateLimit.BucketConfig cfg) {
        Bandwidth limit = Bandwidth.classic(
                cfg.getCapacity(),
                Refill.greedy(cfg.getRefillTokens(),
                        Duration.ofSeconds(cfg.getRefillPeriodSeconds()))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

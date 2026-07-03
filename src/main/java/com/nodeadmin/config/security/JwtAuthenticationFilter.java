package com.nodeadmin.config.security;

import com.nodeadmin.modules.auth.service.IJwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter for {@code /api/**} routes.
 *
 * <p>Executed once per request. Extracts a Bearer token from the
 * {@code Authorization} header, delegates full validation (signature, expiry,
 * blacklist) to {@link IJwtService}, and sets a
 * {@link UsernamePasswordAuthenticationToken} in the {@link SecurityContextHolder}.
 *
 * <p>Paths under {@code /api/v1/auth/**} are skipped entirely — those
 * endpoints issue or revoke tokens and must remain unauthenticated.
 *
 * <p>On any failure (missing token, expired, blacklisted, malformed) the
 * filter continues the chain without setting authentication; Spring Security's
 * {@code HttpStatusEntryPoint(401)} then rejects the request.
 *
 * <p>Delegating to {@link IJwtService} ensures that in tests the
 * {@code TestJwtConfig} in-memory blacklist is used automatically — no Redis
 * wiring required.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** Paths that are always skipped — only token-issuance / revocation endpoints.
     *  /api/v1/auth/me is intentionally NOT skipped — it requires a valid Bearer token. */
    private static final List<String> SKIP_PATTERNS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/auth/register",
            "/api/v1/auth/reset/**"
    );

    private final IJwtService jwtService;

    public JwtAuthenticationFilter(IJwtService jwtService) {
        this.jwtService = jwtService;
    }

    // -------------------------------------------------------------------------
    // OncePerRequestFilter
    // -------------------------------------------------------------------------

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return SKIP_PATTERNS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractBearerToken(request);

        if (StringUtils.hasText(token) && jwtService.validateToken(token)) {
            String userId = jwtService.extractUserId(token);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userId,   // principal = user UUID
                            null,
                            List.of() // no granted authorities needed for API access checks
                    );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}

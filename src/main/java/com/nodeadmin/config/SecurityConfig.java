package com.nodeadmin.config;

import com.nodeadmin.config.security.JwtAuthenticationFilter;
import com.nodeadmin.config.security.UserDetailsServiceImpl;
import com.nodeadmin.modules.auth.service.IJwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Spring Security configuration for SpringAdmin.
 *
 * <p>Two {@link SecurityFilterChain} beans are defined with explicit ordering:
 *
 * <h3>1. apiFilterChain (order 1) — {@code /api/**}</h3>
 * <ul>
 *   <li>Stateless: no session, no CSRF.</li>
 *   <li>JWT Bearer token extracted and validated by {@link JwtAuthenticationFilter}.</li>
 *   <li>Unauthenticated requests receive HTTP 401 JSON (no redirect).</li>
 *   <li>Public paths: {@code /api/v1/auth/**}.</li>
 * </ul>
 *
 * <h3>2. webFilterChain (order 2) — everything else</h3>
 * <ul>
 *   <li>Session-based (Spring Session / Redis).</li>
 *   <li>CSRF enabled, ignoring {@code /api/**}.</li>
 *   <li>401/403 redirect to {@code /auth/login}.</li>
 *   <li>Public paths: {@code /auth/**}, {@code /}, {@code /home},
 *       {@code /public/**}, {@code /vendor/**}, {@code /webjars/**}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // -------------------------------------------------------------------------
    // Shared public paths
    // -------------------------------------------------------------------------

    private static final String[] PUBLIC_PATHS = {
            "/auth/**",
            "/",
            "/home",
            "/public/**",
            "/public/storage/**",
            "/vendor/**",
            "/webjars/**"
    };

    private static final String[] API_PUBLIC_PATHS = {
            "/api/v1/auth/**"
    };

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private final AppProperties          appProperties;
    private final UserDetailsServiceImpl userDetailsService;
    private final IJwtService            jwtService;

    public SecurityConfig(AppProperties appProperties,
                          UserDetailsServiceImpl userDetailsService,
                          IJwtService jwtService) {
        this.appProperties      = appProperties;
        this.userDetailsService = userDetailsService;
        this.jwtService         = jwtService;
    }

    // =========================================================================
    // Filter Chain 1 — API (stateless JWT)
    // =========================================================================

    /**
     * Secures {@code /api/**} with stateless JWT authentication.
     *
     * <p>Order 1 ensures this chain is evaluated before the web chain.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {

        http
            .securityMatcher("/api/**")

            // Security headers — helmet equivalent
            .headers(headers -> headers
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31_536_000))
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(cto -> {})
                    .referrerPolicy(referrer -> referrer
                            .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )

            // Stateless — no HTTP session
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // No CSRF for stateless API
            .csrf(csrf -> csrf.disable())

            // 401 JSON response (no redirect) for unauthenticated API calls
            .exceptionHandling(ex ->
                    ex.authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED)))

            // Public API paths
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(API_PUBLIC_PATHS).permitAll()
                    .anyRequest().authenticated()
            )

            // JWT filter runs before the standard username/password filter
            .addFilterBefore(
                    new JwtAuthenticationFilter(jwtService),
                    UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    // =========================================================================
    // Filter Chain 2 — Web (session-based)
    // =========================================================================

    /**
     * Secures all non-API routes with session-based authentication.
     *
     * <p>Order 2 is the fallback chain.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {

        CsrfTokenRequestAttributeHandler csrfHandler =
                new CsrfTokenRequestAttributeHandler();

        http
            // Security headers — helmet equivalent
            .headers(headers -> headers
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31_536_000))
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(cto -> {})
                    .referrerPolicy(referrer -> referrer
                            .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )

            // CSRF enabled, but exclude /api/** (handled by apiFilterChain)
            .csrf(csrf -> csrf
                    .csrfTokenRequestHandler(csrfHandler)
                    .ignoringRequestMatchers("/api/**")
            )

            // Session-based (Spring Session / Redis configured in application.yml)
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            // Redirect to /auth/login on 401; CSRF denial → 403; other 403 → redirect login
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(
                            new LoginUrlAuthenticationEntryPoint("/auth/login"))
                    .accessDeniedHandler((req, res, denied) -> {
                        if (denied instanceof MissingCsrfTokenException
                                || denied instanceof InvalidCsrfTokenException) {
                            res.sendError(HttpServletResponse.SC_FORBIDDEN,
                                    "CSRF token missing or invalid");
                        } else {
                            res.sendRedirect(req.getContextPath() + "/auth/login");
                        }
                    })
            )

            // Web routes: permit all at the Spring Security layer.
            // Access control for /admin/** is enforced by AccessInterceptor
            // (session-based RBAC, mirrors NodeAdmin's middleware pattern).
            .authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll()
            )

            // Login page redirect only. POST /auth/login is handled exclusively by
            // AuthWebController which calls authService.loginWeb() programmatically
            // and writes FlashHelper.SESSION_USER into the session.
            // loginProcessingUrl is set to a non-existent path to prevent Spring
            // Security from intercepting POST /auth/login before the controller.
            .formLogin(form -> form
                    .loginPage("/auth/login")
                    .loginProcessingUrl("/auth/login/process")
                    .permitAll()
            )

            // Logout
            .logout(logout -> logout
                    .logoutRequestMatcher(new AntPathRequestMatcher("/auth/logout", "POST"))
                    .logoutSuccessUrl("/auth/login?logout=true")
                    .invalidateHttpSession(true)
                    .deleteCookies("SESSION")
                    .permitAll()
            );

        return http.build();
    }

    // =========================================================================
    // Shared beans
    // =========================================================================

    /**
     * BCrypt password encoder. Rounds are configurable via {@code app.bcrypt.rounds}.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(appProperties.getBcrypt().getRounds());
    }

    /**
     * DAO authentication provider wired to our {@link UserDetailsServiceImpl}
     * and BCrypt encoder.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the {@link AuthenticationManager} for use in the auth service
     * (programmatic authentication during login).
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

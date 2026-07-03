package com.nodeadmin.config;

import com.nodeadmin.config.security.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link RateLimitFilter} as a servlet filter restricted to
 * auth-related URL patterns. The filter is applied BEFORE Spring Security
 * (order 1) so abusive clients are rejected before session/CSRF processing.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            AppProperties appProperties) {

        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(appProperties));
        // Covers /auth/login, /auth/register, /auth/reset/request, /auth/reset/process
        // and their /api/v1/auth/* equivalents.
        registration.addUrlPatterns("/auth/*", "/api/v1/auth/*");
        registration.setOrder(1);
        registration.setName("rateLimitFilter");
        return registration;
    }
}

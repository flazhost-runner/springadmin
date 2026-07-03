package com.nodeadmin.modules.security;

import com.nodeadmin.config.AppProperties;
import com.nodeadmin.config.security.RateLimitFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RateLimitFilter} — no Spring context required.
 *
 * <p>Each test builds an {@link AppProperties} with a small capacity (2) so
 * that the 3rd request can be verified to return HTTP 429 without making
 * hundreds of real HTTP calls.
 */
class RateLimitFilterTest {

    private static final String CLIENT_IP = "10.0.0.1";
    private static final String OTHER_IP  = "10.0.0.2";

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(buildProps(2, 1));
    }

    // -------------------------------------------------------------------------
    // Auth paths — /auth/login, /api/v1/auth/login
    // -------------------------------------------------------------------------

    @Test
    void authPath_withinCapacity_shouldPassThrough() throws Exception {
        for (int i = 0; i < 2; i++) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletResponse res = doPost("/auth/login", CLIENT_IP, chain);
            verify(chain, times(1)).doFilter(any(), any());
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void authPath_exceedsCapacity_shouldReturn429() throws Exception {
        // Exhaust bucket
        for (int i = 0; i < 2; i++) {
            doPost("/auth/login", CLIENT_IP, mock(FilterChain.class));
        }
        // Next request must be rate-limited
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = doPost("/auth/login", CLIENT_IP, chain);

        assertThat(res.getStatus()).isEqualTo(429);
        verifyNoInteractions(chain);
    }

    @Test
    void authPath_differentIps_ownSeparateBuckets() throws Exception {
        // Exhaust bucket for CLIENT_IP
        for (int i = 0; i < 2; i++) {
            doPost("/auth/login", CLIENT_IP, mock(FilterChain.class));
        }
        // OTHER_IP should still pass
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = doPost("/auth/login", OTHER_IP, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(any(), any());
    }

    // -------------------------------------------------------------------------
    // OTP paths — /auth/reset/request, /api/v1/auth/reset/request
    // -------------------------------------------------------------------------

    @Test
    void otpPath_exceedsCapacity_shouldReturn429() throws Exception {
        RateLimitFilter otpFilter = new RateLimitFilter(buildProps(100, 1)); // auth=100, otp=1
        // First OTP request passes
        doPost("/auth/reset/request", CLIENT_IP, mock(FilterChain.class), otpFilter);
        // Second is blocked
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = doPost("/auth/reset/request", CLIENT_IP, chain, otpFilter);
        assertThat(res.getStatus()).isEqualTo(429);
        verifyNoInteractions(chain);
    }

    // -------------------------------------------------------------------------
    // Non-POST requests are not rate-limited
    // -------------------------------------------------------------------------

    @Test
    void getRequest_shouldNotBeRateLimited() throws Exception {
        // Exhaust auth bucket first
        for (int i = 0; i < 2; i++) {
            doPost("/auth/login", CLIENT_IP, mock(FilterChain.class));
        }
        // GET request to same path passes regardless
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/auth/login");
        req.setServletPath("/auth/login");
        req.setRemoteAddr(CLIENT_IP);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(any(), any());
    }

    // -------------------------------------------------------------------------
    // Unrelated paths are not rate-limited
    // -------------------------------------------------------------------------

    @Test
    void unrelatedPath_shouldNotBeRateLimited() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = doPost("/admin/v1/users", CLIENT_IP, chain);
        assertThat(res.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(any(), any());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private MockHttpServletResponse doPost(String path, String ip, FilterChain chain)
            throws Exception {
        return doPost(path, ip, chain, filter);
    }

    private MockHttpServletResponse doPost(String path, String ip,
                                            FilterChain chain, RateLimitFilter f)
            throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setServletPath(path);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, chain);
        return res;
    }

    /** Builds AppProperties with given auth capacity and otp capacity. */
    private AppProperties buildProps(int authCapacity, int otpCapacity) {
        AppProperties props = new AppProperties();
        AppProperties.RateLimit rl = new AppProperties.RateLimit();
        rl.setAuth(new AppProperties.RateLimit.BucketConfig(authCapacity, authCapacity, 3600));
        rl.setOtp(new AppProperties.RateLimit.BucketConfig(otpCapacity,  otpCapacity,  3600));
        props.setRateLimit(rl);
        return props;
    }
}

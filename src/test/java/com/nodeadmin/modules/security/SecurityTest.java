package com.nodeadmin.modules.security;

import com.nodeadmin.BaseIntegrationTest;
import com.nodeadmin.common.model.SessionUser;
import com.nodeadmin.common.util.FlashHelper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security integration tests: RBAC, CSRF, and mass-assign protection.
 */
class SecurityTest extends BaseIntegrationTest {

    // -------------------------------------------------------------------------
    // RBAC — unauthenticated access
    // -------------------------------------------------------------------------

    @Test
    void unauthenticated_adminRoute_shouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/admin/v1/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    void unauthenticated_apiRoute_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // RBAC — authenticated access (Administrator bypasses all permission checks)
    // -------------------------------------------------------------------------

    @Test
    void authenticated_adminRoute_shouldReturn200() throws Exception {
        MockHttpSession session = loginAsAdmin();
        assertThat(session).isNotNull();

        SessionUser user = (SessionUser) session.getAttribute(FlashHelper.SESSION_USER);
        assertThat(user).isNotNull();

        mockMvc.perform(get("/admin/v1/dashboard").session(session))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // CSRF — POST without token must be rejected
    // -------------------------------------------------------------------------

    @Test
    void csrfMissing_postLogin_shouldReturn403() throws Exception {
        // POST /auth/login WITHOUT .with(csrf()) — deliberately omitted
        mockMvc.perform(
                post("/auth/login")
                        .param("username", "admin@admin.com")
                        .param("password", "12345678")
        ).andExpect(status().isForbidden());
    }

    @Test
    void csrfPresent_postLogin_shouldNotReturn403() throws Exception {
        // POST /auth/login WITH valid CSRF token — security layer passes it through
        mockMvc.perform(
                post("/auth/login")
                        .with(csrf())
                        .param("username", "admin@admin.com")
                        .param("password", "12345678")
        ).andExpect(status().is3xxRedirection()); // redirect to dashboard on success
    }

    // -------------------------------------------------------------------------
    // Mass-assign — extra fields in register body must not leak into entity
    // -------------------------------------------------------------------------

    @Test
    void massAssign_register_blockedFieldIgnored() throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":     "MA001",
                                  "name":     "Mass Assign Test",
                                  "email":    "massassign_security@example.com",
                                  "password": "MassAssign1!",
                                  "blocked":  true,
                                  "status":   "Blocked"
                                }
                                """)
        ).andExpect(status().isOk());

        userRepository.findAll().stream()
                .filter(u -> "massassign_security@example.com".equals(u.getEmail()))
                .forEach(u -> {
                    assertThat(u.getBlocked()).isFalse();
                    assertThat(u.getStatus()).isEqualTo("Active");
                });
    }
}

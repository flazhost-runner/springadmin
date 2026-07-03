package com.nodeadmin.modules.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nodeadmin.config.TestJwtConfig;
import com.nodeadmin.config.TestProfileResolver;
import com.nodeadmin.modules.access.user.repository.UserRepository;
import com.nodeadmin.modules.auth.service.IAuthService;
import com.nodeadmin.modules.auth.service.IJwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Auth API ({@code /api/v1/auth/**}).
 *
 * <p>Tests the real JWT flow end-to-end:
 * <ul>
 *   <li>POST /api/v1/auth/login → returns JWT token</li>
 *   <li>GET  /api/v1/auth/me   with valid token → 200</li>
 *   <li>POST /api/v1/auth/logout then GET /api/v1/auth/me → 401 (blacklist enforced)</li>
 * </ul>
 *
 * <p>{@link StringRedisTemplate} is mocked out ({@code @MockitoBean}) because
 * the test profile disables Redis autoconfiguration. The JWT blacklist is served
 * by {@link TestJwtConfig} which provides an in-memory implementation marked
 * {@code @Primary}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver.class)
@Transactional
@Import(TestJwtConfig.class)
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IJwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    /**
     * Stub out StringRedisTemplate — SecurityConfig and JwtAuthenticationFilter
     * both receive it via constructor injection; the test IJwtService does not
     * use it, but the wiring still requires a bean of this type.
     */
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    /** Stub out JavaMailSender — OTP emails are not delivered in tests. */
    @MockitoBean
    private JavaMailSender mailSender;

    // -------------------------------------------------------------------------
    // login → returns JWT
    // -------------------------------------------------------------------------

    @Test
    void login_shouldReturnJwtToken() throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@admin.com","password":"12345678"}
                                """)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.token_type").value("Bearer"));
    }

    @Test
    void login_shouldRejectWrongPassword() throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@admin.com","password":"WrongPassword!"}
                                """)
        )
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/auth/me with valid token → 200
    // -------------------------------------------------------------------------

    @Test
    void me_shouldReturn200WithValidToken() throws Exception {
        // Obtain a real token
        String token = obtainToken("admin@admin.com", "12345678");

        mockMvc.perform(
                get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token)
        )
                .andExpect(status().isOk());
    }

    @Test
    void me_shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // logout then /me → 401 (real blacklist behavior)
    // -------------------------------------------------------------------------

    @Test
    void logout_thenMe_shouldReturn401() throws Exception {
        // Step 1: login to obtain token
        String token = obtainToken("admin@admin.com", "12345678");

        // Step 2: confirm /me works before logout
        mockMvc.perform(
                get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token)
        ).andExpect(status().isOk());

        // Step 3: logout (blacklists the token)
        mockMvc.perform(
                post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token)
        ).andExpect(status().isOk());

        // Step 4: verify token is now blacklisted
        assertThat(jwtService.isBlacklisted(token)).isTrue();

        // Step 5: /me with the revoked token must return 401
        mockMvc.perform(
                get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token)
        ).andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Performs a login and extracts the {@code access_token} from the JSON response.
     */
    @SuppressWarnings("unchecked")
    private String obtainToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", password)))
        ).andExpect(status().isOk()).andReturn();

        String body = result.getResponse().getContentAsString();
        Map<String, Object> json = objectMapper.readValue(body, Map.class);
        Map<String, Object> data = (Map<String, Object>) json.get("data");
        return (String) data.get("access_token");
    }
}

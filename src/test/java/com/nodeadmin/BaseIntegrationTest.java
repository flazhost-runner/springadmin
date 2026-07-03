package com.nodeadmin;

import com.nodeadmin.config.TestJwtConfig;
import com.nodeadmin.config.TestProfileResolver;
import com.nodeadmin.modules.access.permission.repository.PermissionRepository;
import com.nodeadmin.modules.access.role.entity.RoleEntity;
import com.nodeadmin.modules.access.role.repository.RoleRepository;
import com.nodeadmin.modules.access.user.entity.UserEntity;
import com.nodeadmin.modules.access.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Abstract base class for Spring Boot integration tests.
 *
 * <p>Provides shared infrastructure:
 * <ul>
 *   <li>Full Spring context with H2 in-memory database (profile "test").</li>
 *   <li>{@link MockMvc} for HTTP-layer testing without a real server.</li>
 *   <li>Helper methods for creating test users and logging in as admin.</li>
 *   <li>{@link Transactional} — each test rolls back automatically.</li>
 * </ul>
 *
 * <p>The "test" profile activates {@code application-test.yml}, which disables
 * Redis and replaces the datasource with H2 in MySQL-compatibility mode.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver.class)
@Transactional
@Import(TestJwtConfig.class)
public abstract class BaseIntegrationTest {

    /**
     * Stub out StringRedisTemplate — SecurityConfig and JwtAuthenticationFilter
     * require it, but the test profile uses in-memory JWT blacklisting via
     * TestJwtConfig instead of Redis.
     */
    @MockitoBean
    protected StringRedisTemplate stringRedisTemplate;

    /** Stub out JavaMailSender — OTP emails are not delivered in tests. */
    @MockitoBean
    protected JavaMailSender mailSender;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected PermissionRepository permissionRepository;

    // -------------------------------------------------------------------------
    // Helper: createTestUser
    // -------------------------------------------------------------------------

    /**
     * Creates and persists a minimal {@link UserEntity} with the given code and
     * email. A random UUID is assigned as the ID; the password is BCrypt-encoded
     * from the literal string {@code "Test1234!"}. The user is given the seeded
     * "Administrator" role if it exists, otherwise no role is assigned.
     *
     * @param code  unique user code (e.g. {@code "TST001"})
     * @param email unique email address
     * @return the saved {@link UserEntity}
     */
    protected UserEntity createTestUser(String code, String email) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID().toString());
        user.setCode(code);
        user.setName("Test User " + code);
        user.setEmail(email);
        // Low BCrypt rounds (4) are set in application-test.yml via app.bcrypt.rounds
        user.setPassword(new BCryptPasswordEncoder(4).encode("Test1234!"));
        user.setStatus("Active");
        user.setTimezone("UTC");

        // Assign Administrator role if seeded
        roleRepository.findByName("Administrator").ifPresent(role ->
                user.setRoles(Set.of(role))
        );

        return userRepository.save(user);
    }

    // -------------------------------------------------------------------------
    // Helper: loginAsAdmin
    // -------------------------------------------------------------------------

    /**
     * Performs a form-based login as the seeded admin user
     * ({@code admin@admin.com} / {@code 12345678}) and returns the resulting
     * {@link MockHttpSession} so that subsequent requests can be made as an
     * authenticated user.
     *
     * <p>Uses Spring Security's CSRF post processor to satisfy the CSRF filter.
     *
     * @return a live {@link MockHttpSession} authenticated as admin
     * @throws Exception if the login POST fails unexpectedly
     */
    protected MockHttpSession loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(
                post("/auth/login")
                        .with(csrf())
                        .param("email", "admin@admin.com")
                        .param("password", "12345678")
        ).andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    // -------------------------------------------------------------------------
    // Helper: getCsrfToken
    // -------------------------------------------------------------------------

    /**
     * Retrieves the CSRF token string from a GET request to the login page.
     *
     * <p>Thymeleaf renders the hidden {@code _csrf} field; in tests the token is
     * accessible from the Spring Security request attribute
     * {@code _csrf} (type {@link org.springframework.security.web.csrf.CsrfToken}).
     *
     * @return the CSRF token value as a String, or an empty string if unavailable
     * @throws Exception if the GET request fails
     */
    protected String getCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/login")).andReturn();
        Object csrfToken = result.getRequest().getAttribute("_csrf");
        if (csrfToken instanceof org.springframework.security.web.csrf.CsrfToken token) {
            return token.getToken();
        }
        // Fallback: token may be stored as a deferred supplier — resolve it
        Object deferred = result.getRequest().getAttribute(
                org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
                        .class.getName().concat(".DEFERRED_CSRF_TOKEN"));
        if (deferred instanceof java.util.function.Supplier<?> supplier) {
            Object supplied = supplier.get();
            if (supplied instanceof org.springframework.security.web.csrf.CsrfToken ct) {
                return ct.getToken();
            }
        }
        return "";
    }
}

package com.nodeadmin.modules.access;

import com.nodeadmin.BaseIntegrationTest;
import com.nodeadmin.common.error.AppError;
import com.nodeadmin.modules.access.user.dto.UserRequest;
import com.nodeadmin.modules.access.user.entity.UserEntity;
import com.nodeadmin.modules.access.user.service.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link IUserService}.
 *
 * <p>Each test runs inside a transaction that is rolled back after completion,
 * so the H2 database state is clean for every test.
 *
 * <p>Covers:
 * <ul>
 *   <li>Store user — happy path</li>
 *   <li>Store user — reject duplicate email</li>
 *   <li>Update user</li>
 *   <li>Delete user</li>
 *   <li>Pagination with filters</li>
 * </ul>
 */
class UserServiceTest extends BaseIntegrationTest {

    @Autowired
    private IUserService userService;

    private String adminRoleId;

    @BeforeEach
    void setUp() {
        // Resolve the seeded Administrator role id
        adminRoleId = roleRepository.findByName("Administrator")
                .map(r -> r.getId())
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // store — happy path
    // -------------------------------------------------------------------------

    @Test
    void store_shouldPersistUser() {
        UserRequest req = buildRequest("USR001", "store_test@example.com", "Password1!");
        if (adminRoleId != null) req.setRoles(List.of(adminRoleId));

        UserEntity saved = userService.store(req, "system", null);

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getEmail()).isEqualTo("store_test@example.com");
        assertThat(saved.getCode()).isEqualTo("USR001");
        // Password must be hashed
        assertThat(saved.getPassword()).doesNotContain("Password1!");
        assertThat(saved.getPassword()).startsWith("$2");
    }

    // -------------------------------------------------------------------------
    // store — reject duplicate email
    // -------------------------------------------------------------------------

    @Test
    void store_shouldRejectDuplicateEmail() {
        // Create first user
        UserRequest req1 = buildRequest("USR002", "dup@example.com", "Password1!");
        if (adminRoleId != null) req1.setRoles(List.of(adminRoleId));
        userService.store(req1, "system", null);

        // Flush so the unique constraint is visible
        userRepository.flush();

        // Attempt to create second user with same email
        UserRequest req2 = buildRequest("USR003", "dup@example.com", "Password1!");
        if (adminRoleId != null) req2.setRoles(List.of(adminRoleId));

        assertThatThrownBy(() -> {
            userService.store(req2, "system", null);
            userRepository.flush();
        }).satisfies(ex -> {
            // The exception is either AppError (from service-level check)
            // or a JPA DataIntegrityViolationException from the DB unique constraint.
            // Both are acceptable — we just assert something was thrown.
            assertThat(ex).isInstanceOf(Exception.class);
        });
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void update_shouldChangeUserFields() {
        // Seed a user directly
        UserEntity existing = createTestUser("USR010", "update_test@example.com");

        UserRequest req = new UserRequest();
        req.setName("Updated Name");
        req.setCode("USR010");
        req.setEmail("update_test@example.com");
        req.setStatus("Inactive");
        req.setTimezone("Asia/Jakarta");
        if (adminRoleId != null) req.setRoles(List.of(adminRoleId));

        UserEntity updated = userService.update(existing.getId(), req, "system", null);

        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getStatus()).isEqualTo("Inactive");
        assertThat(updated.getTimezone()).isEqualTo("Asia/Jakarta");
    }

    @Test
    void update_shouldRehashPasswordOnlyWhenSupplied() {
        UserEntity existing = createTestUser("USR011", "pwd_test@example.com");
        String originalHash = existing.getPassword();

        UserRequest reqNoPassword = new UserRequest();
        reqNoPassword.setName("No Password Change");
        reqNoPassword.setCode("USR011");
        reqNoPassword.setEmail("pwd_test@example.com");
        reqNoPassword.setPassword(""); // blank — should not rehash
        if (adminRoleId != null) reqNoPassword.setRoles(List.of(adminRoleId));

        UserEntity updated = userService.update(existing.getId(), reqNoPassword, "system", null);
        assertThat(updated.getPassword()).isEqualTo(originalHash);

        // Now supply a new password
        UserRequest reqWithPassword = new UserRequest();
        reqWithPassword.setName("No Password Change");
        reqWithPassword.setCode("USR011");
        reqWithPassword.setEmail("pwd_test@example.com");
        reqWithPassword.setPassword("NewPassword1!");
        if (adminRoleId != null) reqWithPassword.setRoles(List.of(adminRoleId));

        UserEntity updatedWithPwd = userService.update(existing.getId(), reqWithPassword, "system", null);
        assertThat(updatedWithPwd.getPassword()).isNotEqualTo(originalHash);
        assertThat(updatedWithPwd.getPassword()).startsWith("$2");
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_shouldRemoveUser() {
        UserEntity existing = createTestUser("USR020", "delete_test@example.com");
        String id = existing.getId();

        userService.delete(id);

        assertThat(userRepository.findById(id)).isEmpty();
    }

    @Test
    void delete_shouldThrowWhenUserNotFound() {
        assertThatThrownBy(() -> userService.delete("non-existent-id"))
                .isInstanceOf(AppError.class);
    }

    // -------------------------------------------------------------------------
    // pagination with filters
    // -------------------------------------------------------------------------

    @Test
    void index_shouldReturnPaginatedResults() {
        // Create several users
        for (int i = 1; i <= 5; i++) {
            createTestUser("PG%03d".formatted(i), "paginate%d@example.com".formatted(i));
        }

        Map<String, Object> result = userService.index(Map.of(
                "q_page", "1",
                "q_page_size", "3"
        ));

        assertThat(result).containsKeys("data", "total", "page", "pageSize", "totalPages");
        @SuppressWarnings("unchecked")
        List<?> data = (List<?>) result.get("data");
        assertThat(data.size()).isLessThanOrEqualTo(3);
        assertThat((Long) result.get("total")).isGreaterThanOrEqualTo(5L);
    }

    @Test
    void index_shouldFilterByEmail() {
        createTestUser("FLT001", "filter_unique_xyz@example.com");

        Map<String, Object> result = userService.index(Map.of(
                "q_email", "filter_unique_xyz"
        ));

        @SuppressWarnings("unchecked")
        List<UserEntity> data = (List<UserEntity>) result.get("data");
        assertThat(data).hasSize(1);
        assertThat(data.get(0).getEmail()).isEqualTo("filter_unique_xyz@example.com");
    }

    @Test
    void index_shouldFilterByStatus() {
        createTestUser("STS001", "active_status@example.com");

        Map<String, Object> resultActive = userService.index(Map.of(
                "q_status", "Active",
                "q_page_size", "100"
        ));

        @SuppressWarnings("unchecked")
        List<UserEntity> activeUsers = (List<UserEntity>) resultActive.get("data");
        assertThat(activeUsers).allMatch(u -> "Active".equals(u.getStatus()));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private UserRequest buildRequest(String code, String email, String password) {
        UserRequest req = new UserRequest();
        req.setCode(code);
        req.setName("Test " + code);
        req.setEmail(email);
        req.setPassword(password);
        req.setStatus("Active");
        req.setTimezone("UTC");
        return req;
    }
}

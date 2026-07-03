package com.nodeadmin.bdd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nodeadmin.modules.access.user.entity.UserEntity;
import com.nodeadmin.modules.access.user.repository.UserRepository;
import com.nodeadmin.modules.access.role.repository.RoleRepository;
import com.nodeadmin.modules.auth.service.IJwtService;
import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cucumber step definitions for {@code delete_method_smoke.feature}.
 *
 * <p>Covers two scenarios:
 * <ol>
 *   <li>Delete user via form POST with {@code _method=DELETE} (HiddenHttpMethodFilter).</li>
 *   <li>Verbose API paths (NodeAdmin-style) return 200 for JWT-authenticated users.</li>
 * </ol>
 *
 * <p>Wired into the Spring Boot test context by {@link CucumberSpringConfig}.
 */
public class DeleteMethodSmokeSteps {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private IJwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    // ── shared state across steps within one scenario ──────────────────────

    private MockHttpSession session;
    private String          testUserId;
    private String          testUserEmail;
    private MvcResult       lastResult;
    private String          jwtToken;

    // ── cleanup after each scenario ────────────────────────────────────────

    @After
    public void tearDown() {
        // Remove test user if it still exists (scenario may have deleted it)
        if (testUserId != null) {
            userRepository.findById(testUserId).ifPresent(userRepository::delete);
        }
    }

    // ==========================================================================
    // Background
    // ==========================================================================

    @Given("the application is running")
    public void the_application_is_running() {
        // Spring context started by CucumberSpringConfig — nothing extra needed
        assertThat(mockMvc).isNotNull();
    }

    // ==========================================================================
    // Scenario 1 — delete via _method=DELETE
    // ==========================================================================

    @Given("I am logged in as admin")
    public void i_am_logged_in_as_admin() throws Exception {
        MvcResult result = mockMvc.perform(
                post("/auth/login")
                        .with(csrf())
                        .param("email", "admin@admin.com")
                        .param("password", "12345678")
        ).andReturn();
        session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).as("Login should produce a valid session").isNotNull();
    }

    @And("a test user exists with code {string} and email {string}")
    public void a_test_user_exists_with_code_and_email(String code, String email) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID().toString());
        user.setCode(code);
        user.setName("BDD Test User");
        user.setEmail(email);
        user.setPassword(new BCryptPasswordEncoder(4).encode("Test1234!"));
        user.setStatus("Active");
        user.setTimezone("UTC");
        roleRepository.findByName("Administrator").ifPresent(r -> user.setRoles(Set.of(r)));

        UserEntity saved = userRepository.save(user);
        testUserId    = saved.getId();
        testUserEmail = saved.getEmail();
    }

    @When("I submit a POST request to {string} with {string} = {string}")
    public void i_submit_a_post_request_with_method_override(
            String urlTemplate, String paramName, String paramValue) throws Exception {

        String url = urlTemplate.replace("{id}", testUserId);
        lastResult = mockMvc.perform(
                post(url)
                        .with(csrf())
                        .session(session)
                        .param(paramName, paramValue)
        ).andReturn();
    }

    @Then("the response status should be in range {int} to {int}")
    public void the_response_status_should_be_in_range(int low, int high) {
        int status = lastResult.getResponse().getStatus();
        assertThat(status)
                .as("Expected HTTP status between %d and %d but got %d", low, high, status)
                .isBetween(low, high);
    }

    @And("the user {string} should no longer exist")
    public void the_user_should_no_longer_exist(String email) {
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    // ==========================================================================
    // Scenario 2 — verbose API paths return 200
    // ==========================================================================

    @Given("I am authenticated via JWT as admin")
    public void i_am_authenticated_via_jwt_as_admin() throws Exception {
        MvcResult result = mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@admin.com","password":"12345678"}
                                """)
        ).andExpect(status().isOk()).andReturn();

        String body = result.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> json = objectMapper.readValue(body, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) json.get("data");
        jwtToken = (String) data.get("access_token");
        assertThat(jwtToken).isNotBlank();
    }

    @When("I send a GET request to {string}")
    public void i_send_a_get_request_to(String url) throws Exception {
        lastResult = mockMvc.perform(
                get(url).header("Authorization", "Bearer " + jwtToken)
        ).andReturn();
    }

    @Then("the response status should be {int}")
    public void the_response_status_should_be(int expectedStatus) {
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(expectedStatus);
    }

    @And("the response body should contain a {string} field")
    public void the_response_body_should_contain_a_field(String fieldName) throws Exception {
        String body = lastResult.getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> json = objectMapper.readValue(body, Map.class);
        assertThat(json).containsKey(fieldName);
    }
}

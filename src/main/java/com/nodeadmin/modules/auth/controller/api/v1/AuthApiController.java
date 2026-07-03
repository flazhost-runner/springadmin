package com.nodeadmin.modules.auth.controller.api.v1;

import com.nodeadmin.common.error.UnauthorizedError;
import com.nodeadmin.common.model.SessionUser;
import com.nodeadmin.common.response.ResponseHandler;
import com.nodeadmin.common.route.RouteRegistry;
import com.nodeadmin.common.util.FlashHelper;
import com.nodeadmin.modules.access.user.entity.UserEntity;
import com.nodeadmin.modules.access.user.repository.UserRepository;
import com.nodeadmin.modules.auth.dto.LoginRequest;
import com.nodeadmin.modules.auth.dto.OtpRequest;
import com.nodeadmin.modules.auth.dto.RegisterRequest;
import com.nodeadmin.modules.auth.service.IAuthService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST (token-based) authentication controller.
 *
 * <p>Route names follow the NodeAdmin {@code namedRoutes} convention:
 * <ul>
 *   <li>{@code api.v1.auth.login}          — POST /api/v1/auth/login</li>
 *   <li>{@code api.v1.auth.logout}         — POST /api/v1/auth/logout  (NOT GET)</li>
 *   <li>{@code api.v1.auth.me}             — GET  /api/v1/auth/me</li>
 *   <li>{@code api.v1.auth.register}       — POST /api/v1/auth/register</li>
 *   <li>{@code api.v1.auth.reset.request}  — POST /api/v1/auth/reset/request</li>
 *   <li>{@code api.v1.auth.reset.process}  — POST /api/v1/auth/reset/process</li>
 * </ul>
 *
 * <p>All responses are wrapped by {@link ResponseHandler} in the standard
 * {@code { status, message, data }} envelope.
 *
 * <p>The Bearer token is expected in the {@code Authorization} header for
 * logout and {@code /me} endpoints. The token is blacklisted in Redis by
 * {@link IAuthService#logoutApi(String)}, mirroring NodeAdmin's
 * {@code clientRedis.v4.set(token, 'blacklisted', { EX: ttlSec })} approach.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthApiController {

    private final IAuthService   authService;
    private final UserRepository userRepository;
    private final RouteRegistry  routeRegistry;

    public AuthApiController(IAuthService authService,
                             UserRepository userRepository,
                             RouteRegistry routeRegistry) {
        this.authService    = authService;
        this.userRepository = userRepository;
        this.routeRegistry  = routeRegistry;
    }

    @PostConstruct
    public void registerRoutes() {
        routeRegistry.register("api.v1.auth.login",         "POST", "/api/v1/auth/login");
        routeRegistry.register("api.v1.auth.logout",        "POST", "/api/v1/auth/logout");
        routeRegistry.register("api.v1.auth.me",            "GET",  "/api/v1/auth/me");
        routeRegistry.register("api.v1.auth.register",      "POST", "/api/v1/auth/register");
        routeRegistry.register("api.v1.auth.reset.request", "POST", "/api/v1/auth/reset/request");
        routeRegistry.register("api.v1.auth.reset.process", "POST", "/api/v1/auth/reset/process");
    }

    // =========================================================================
    // Login — name: api.v1.auth.login
    // =========================================================================

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest loginRequest) {
        String token = authService.loginApi(loginRequest.getEmail(), loginRequest.getPassword());
        Map<String, Object> data = Map.of(
                "access_token", token,
                "token_type",   "Bearer",
                "expires_in",   86400
        );
        return ResponseHandler.success("Ok", data);
    }

    // =========================================================================
    // Logout — name: api.v1.auth.logout  (POST, not GET)
    // =========================================================================

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractBearerToken(authHeader);
        authService.logoutApi(token);
        return ResponseHandler.success("Logged out successfully");
    }

    // =========================================================================
    // Me — name: api.v1.auth.me
    // =========================================================================

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpSession session) {
        // For API (JWT) requests the JwtAuthenticationFilter sets the principal as userId.
        // Fall back to session for web-context calls.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof String userId
                && !userId.equals("anonymousUser")) {
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new UnauthorizedError("Not authenticated"));
            Map<String, Object> data = Map.of(
                    "id",    user.getId(),
                    "email", user.getEmail(),
                    "name",  user.getName(),
                    "code",  user.getCode()
            );
            return ResponseHandler.success("Ok", data);
        }
        // Session fallback for web-based calls
        SessionUser currentUser = (SessionUser) session.getAttribute(FlashHelper.SESSION_USER);
        if (currentUser == null) {
            throw new UnauthorizedError("Not authenticated");
        }
        return ResponseHandler.success("Ok", currentUser);
    }

    // =========================================================================
    // Register — name: api.v1.auth.register
    // =========================================================================

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest registerRequest) {
        UserEntity user = authService.register(registerRequest);
        Map<String, Object> data = Map.of("id", user.getId(), "email", user.getEmail());
        return ResponseHandler.success("User registered successfully", data);
    }

    // =========================================================================
    // Password reset — name: api.v1.auth.reset.request
    // =========================================================================

    @PostMapping("/reset/request")
    public ResponseEntity<Map<String, Object>> resetRequest(
            @RequestParam(required = false) String email,
            @RequestBody(required = false) Map<String, String> body) {
        String resolvedEmail = (email != null) ? email
                : (body != null ? body.get("email") : null);
        if (resolvedEmail == null || resolvedEmail.isBlank()) {
            throw new UnauthorizedError("Email is required");
        }
        authService.requestOtp(resolvedEmail);
        return ResponseHandler.success("OTP sent successfully");
    }

    // =========================================================================
    // Password reset — name: api.v1.auth.reset.process
    // =========================================================================

    @PostMapping("/reset/process")
    public ResponseEntity<Map<String, Object>> resetProcess(
            @Valid @RequestBody OtpRequest otpRequest) {
        authService.processOtp(
                otpRequest.getEmail(),
                otpRequest.getOtp(),
                otpRequest.getNewPassword()
        );
        return ResponseHandler.success("Password reset successfully");
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new UnauthorizedError("No Bearer token provided");
    }
}

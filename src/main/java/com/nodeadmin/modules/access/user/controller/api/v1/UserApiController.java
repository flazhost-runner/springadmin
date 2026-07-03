package com.nodeadmin.modules.access.user.controller.api.v1;

import com.nodeadmin.common.response.ResponseHandler;
import com.nodeadmin.common.route.RouteRegistry;
import com.nodeadmin.modules.access.user.dto.UserRequest;
import com.nodeadmin.modules.access.user.entity.UserEntity;
import com.nodeadmin.modules.access.user.service.IUserService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST (token-based) controller for the User resource.
 *
 * <p>All paths are VERBOSE (not REST-style), following NodeAdmin's API routing:
 * <ul>
 *   <li>GET    /api/v1/access/user              — index</li>
 *   <li>POST   /api/v1/access/user/store        — store</li>
 *   <li>GET    /api/v1/access/user/{id}/edit    — edit</li>
 *   <li>PUT    /api/v1/access/user/{id}/update  — update</li>
 *   <li>DELETE /api/v1/access/user/{id}/delete  — delete</li>
 *   <li>POST   /api/v1/access/user/delete_selected — deleteSelected</li>
 * </ul>
 *
 * <p>All responses are wrapped by {@link ResponseHandler} in the standard
 * {@code { status, message, data }} envelope.
 */
@RestController
@RequestMapping("/api/v1/access/user")
public class UserApiController {

    private final IUserService  userService;
    private final RouteRegistry routeRegistry;

    public UserApiController(IUserService userService, RouteRegistry routeRegistry) {
        this.userService   = userService;
        this.routeRegistry = routeRegistry;
    }

    @PostConstruct
    public void registerRoutes() {
        routeRegistry.register("api.v1.access.user.index",          "GET",    "/api/v1/access/user");
        routeRegistry.register("api.v1.access.user.store",          "POST",   "/api/v1/access/user/store");
        routeRegistry.register("api.v1.access.user.edit",           "GET",    "/api/v1/access/user/{id}/edit");
        routeRegistry.register("api.v1.access.user.update",         "PUT",    "/api/v1/access/user/{id}/update");
        routeRegistry.register("api.v1.access.user.delete",         "DELETE", "/api/v1/access/user/{id}/delete");
        routeRegistry.register("api.v1.access.user.delete_selected","POST",   "/api/v1/access/user/delete_selected");
    }

    // =========================================================================
    // Index — GET /api/v1/access/user
    // =========================================================================

    @GetMapping
    public ResponseEntity<Map<String, Object>> index(@RequestParam Map<String, String> filters) {
        Map<String, Object> result = userService.index(filters);
        return ResponseHandler.success("Ok", result);
    }

    // =========================================================================
    // Store — POST /api/v1/access/user/store
    // =========================================================================

    @PostMapping("/store")
    public ResponseEntity<Map<String, Object>> store(
            @RequestBody @Valid UserRequest userRequest,
            @RequestHeader(value = "X-Actor-Id", required = false, defaultValue = "system") String actorId) {
        UserEntity user = userService.store(userRequest, actorId, null);
        return ResponseHandler.success("User created successfully", Map.of("id", user.getId()));
    }

    // =========================================================================
    // Edit — GET /api/v1/access/user/{id}/edit
    // =========================================================================

    @GetMapping("/{id}/edit")
    public ResponseEntity<Map<String, Object>> edit(@PathVariable String id) {
        Map<String, Object> result = userService.edit(id);
        return ResponseHandler.success("Ok", result);
    }

    // =========================================================================
    // Update — PUT /api/v1/access/user/{id}/update
    // =========================================================================

    @PutMapping("/{id}/update")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String id,
            @RequestBody @Valid UserRequest userRequest,
            @RequestHeader(value = "X-Actor-Id", required = false, defaultValue = "system") String actorId) {
        UserEntity user = userService.update(id, userRequest, actorId, null);
        return ResponseHandler.success("User updated successfully", Map.of("id", user.getId()));
    }

    // =========================================================================
    // Delete — DELETE /api/v1/access/user/{id}/delete
    // =========================================================================

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        userService.delete(id);
        return ResponseHandler.success("User deleted successfully");
    }

    // =========================================================================
    // Delete Selected — POST /api/v1/access/user/delete_selected
    // =========================================================================

    @PostMapping("/delete_selected")
    public ResponseEntity<Map<String, Object>> deleteSelected(
            @RequestBody Map<String, List<String>> body) {
        List<String> ids = body.get("ids");
        userService.deleteSelected(ids);
        return ResponseHandler.success("Selected users deleted successfully");
    }
}

package com.nodeadmin.modules.access.role.controller.api.v1;

import com.nodeadmin.common.response.ResponseHandler;
import com.nodeadmin.common.route.RouteRegistry;
import com.nodeadmin.modules.access.role.dto.RoleRequest;
import com.nodeadmin.modules.access.role.entity.RoleEntity;
import com.nodeadmin.modules.access.role.service.IRoleService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST (token-based) controller for the Role resource.
 *
 * <p>All paths are VERBOSE (not REST-style):
 * <ul>
 *   <li>GET    /api/v1/access/role              — index</li>
 *   <li>POST   /api/v1/access/role/store        — store</li>
 *   <li>GET    /api/v1/access/role/{id}/edit    — edit</li>
 *   <li>PUT    /api/v1/access/role/{id}/update  — update</li>
 *   <li>DELETE /api/v1/access/role/{id}/delete  — delete</li>
 *   <li>POST   /api/v1/access/role/delete_selected — deleteSelected</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/access/role")
public class RoleApiController {

    private final IRoleService  roleService;
    private final RouteRegistry routeRegistry;

    public RoleApiController(IRoleService roleService, RouteRegistry routeRegistry) {
        this.roleService   = roleService;
        this.routeRegistry = routeRegistry;
    }

    @PostConstruct
    public void registerRoutes() {
        routeRegistry.register("api.v1.access.role.index",          "GET",    "/api/v1/access/role");
        routeRegistry.register("api.v1.access.role.store",          "POST",   "/api/v1/access/role/store");
        routeRegistry.register("api.v1.access.role.edit",           "GET",    "/api/v1/access/role/{id}/edit");
        routeRegistry.register("api.v1.access.role.update",         "PUT",    "/api/v1/access/role/{id}/update");
        routeRegistry.register("api.v1.access.role.delete",         "DELETE", "/api/v1/access/role/{id}/delete");
        routeRegistry.register("api.v1.access.role.delete_selected","POST",   "/api/v1/access/role/delete_selected");
    }

    // =========================================================================
    // Index — GET /api/v1/access/role
    // =========================================================================

    @GetMapping
    public ResponseEntity<Map<String, Object>> index(@RequestParam Map<String, String> filters) {
        return ResponseHandler.success("Ok", roleService.index(filters));
    }

    // =========================================================================
    // Store — POST /api/v1/access/role/store
    // =========================================================================

    @PostMapping("/store")
    public ResponseEntity<Map<String, Object>> store(@Valid @RequestBody RoleRequest roleRequest) {
        RoleEntity role = roleService.store(roleRequest);
        return ResponseHandler.success("Role created successfully", Map.of("id", role.getId()));
    }

    // =========================================================================
    // Edit — GET /api/v1/access/role/{id}/edit
    // =========================================================================

    @GetMapping("/{id}/edit")
    public ResponseEntity<Map<String, Object>> edit(@PathVariable String id) {
        return ResponseHandler.success("Ok", roleService.edit(id));
    }

    // =========================================================================
    // Update — PUT /api/v1/access/role/{id}/update
    // =========================================================================

    @PutMapping("/{id}/update")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String id,
                                                       @Valid @RequestBody RoleRequest roleRequest) {
        RoleEntity role = roleService.update(id, roleRequest);
        return ResponseHandler.success("Role updated successfully", Map.of("id", role.getId()));
    }

    // =========================================================================
    // Delete — DELETE /api/v1/access/role/{id}/delete
    // =========================================================================

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        roleService.delete(id);
        return ResponseHandler.success("Role deleted successfully");
    }

    // =========================================================================
    // Delete Selected — POST /api/v1/access/role/delete_selected
    // =========================================================================

    @PostMapping("/delete_selected")
    public ResponseEntity<Map<String, Object>> deleteSelected(
            @RequestBody Map<String, List<String>> body) {
        List<String> ids = body.get("ids");
        roleService.deleteSelected(ids);
        return ResponseHandler.success("Selected roles deleted successfully");
    }
}

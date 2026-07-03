package com.nodeadmin.modules.access.permission.controller.api.v1;

import com.nodeadmin.common.response.ResponseHandler;
import com.nodeadmin.common.route.RouteRegistry;
import com.nodeadmin.modules.access.permission.dto.PermissionRequest;
import com.nodeadmin.modules.access.permission.entity.PermissionEntity;
import com.nodeadmin.modules.access.permission.service.IPermissionService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST (token-based) controller for the Permission resource.
 *
 * <p>All paths are VERBOSE (not REST-style):
 * <ul>
 *   <li>GET    /api/v1/access/permission              — index (also syncs routes)</li>
 *   <li>POST   /api/v1/access/permission/store        — store</li>
 *   <li>GET    /api/v1/access/permission/{id}/edit    — edit</li>
 *   <li>PUT    /api/v1/access/permission/{id}/update  — update</li>
 *   <li>DELETE /api/v1/access/permission/{id}/delete  — delete</li>
 *   <li>POST   /api/v1/access/permission/delete_selected — deleteSelected</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/access/permission")
public class PermissionApiController {

    private final IPermissionService permissionService;
    private final RouteRegistry      routeRegistry;

    public PermissionApiController(IPermissionService permissionService,
                                   RouteRegistry routeRegistry) {
        this.permissionService = permissionService;
        this.routeRegistry     = routeRegistry;
    }

    @PostConstruct
    public void registerRoutes() {
        routeRegistry.register("api.v1.access.permission.index",          "GET",    "/api/v1/access/permission");
        routeRegistry.register("api.v1.access.permission.store",          "POST",   "/api/v1/access/permission/store");
        routeRegistry.register("api.v1.access.permission.edit",           "GET",    "/api/v1/access/permission/{id}/edit");
        routeRegistry.register("api.v1.access.permission.update",         "PUT",    "/api/v1/access/permission/{id}/update");
        routeRegistry.register("api.v1.access.permission.delete",         "DELETE", "/api/v1/access/permission/{id}/delete");
        routeRegistry.register("api.v1.access.permission.delete_selected","POST",   "/api/v1/access/permission/delete_selected");
    }

    // =========================================================================
    // Index — GET /api/v1/access/permission
    // =========================================================================

    @GetMapping
    public ResponseEntity<Map<String, Object>> index(@RequestParam Map<String, String> filters) {
        // Lazy sync: upsert any new routes as permissions (mirrors web controller)
        permissionService.syncFromRoutes(routeRegistry.all());
        return ResponseHandler.success("Ok", permissionService.index(filters));
    }

    // =========================================================================
    // Store — POST /api/v1/access/permission/store
    // =========================================================================

    @PostMapping("/store")
    public ResponseEntity<Map<String, Object>> store(
            @Valid @RequestBody PermissionRequest permissionRequest) {
        PermissionEntity perm = permissionService.store(permissionRequest);
        return ResponseHandler.success("Permission created successfully", Map.of("id", perm.getId()));
    }

    // =========================================================================
    // Edit — GET /api/v1/access/permission/{id}/edit
    // =========================================================================

    @GetMapping("/{id}/edit")
    public ResponseEntity<Map<String, Object>> edit(@PathVariable String id) {
        return ResponseHandler.success("Ok", permissionService.edit(id));
    }

    // =========================================================================
    // Update — PUT /api/v1/access/permission/{id}/update
    // =========================================================================

    @PutMapping("/{id}/update")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String id,
                                                       @Valid @RequestBody PermissionRequest permissionRequest) {
        PermissionEntity perm = permissionService.update(id, permissionRequest);
        return ResponseHandler.success("Permission updated successfully", Map.of("id", perm.getId()));
    }

    // =========================================================================
    // Delete — DELETE /api/v1/access/permission/{id}/delete
    // =========================================================================

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        permissionService.delete(id);
        return ResponseHandler.success("Permission deleted successfully");
    }

    // =========================================================================
    // Delete Selected — POST /api/v1/access/permission/delete_selected
    // =========================================================================

    @PostMapping("/delete_selected")
    public ResponseEntity<Map<String, Object>> deleteSelected(
            @RequestBody Map<String, List<String>> body) {
        List<String> ids = body.get("ids");
        permissionService.deleteSelected(ids);
        return ResponseHandler.success("Selected permissions deleted successfully");
    }
}

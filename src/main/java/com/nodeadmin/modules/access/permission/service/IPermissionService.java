package com.nodeadmin.modules.access.permission.service;

import com.nodeadmin.common.route.RouteDefinition;
import com.nodeadmin.common.util.PaginateResult;
import com.nodeadmin.modules.access.permission.dto.PermissionRequest;
import com.nodeadmin.modules.access.permission.entity.PermissionEntity;

import java.util.List;
import java.util.Map;

/**
 * Service contract for the Permission resource in the access module.
 *
 * <p>Mirrors NodeAdmin's {@code IPermissionService}:
 * <ul>
 *   <li>CRUD: {@link #index}, {@link #store}, {@link #edit}, {@link #update},
 *       {@link #delete}, {@link #deleteSelected}</li>
 *   <li>Route sync: {@link #syncFromRoutes} — idempotent upsert of permissions
 *       derived from registered routes, called lazily when the Permission index
 *       page loads.</li>
 * </ul>
 *
 * <p>Supported filter keys for {@link #index}: {@code q_name}, {@code q_method},
 * {@code q_status}, {@code q_guard}, {@code q_desc}, {@code q_page},
 * {@code q_page_size}.
 */
public interface IPermissionService {

    /**
     * Returns a paginated list of permissions, optionally filtered.
     *
     * @param filters raw request params with {@code q_*} prefix keys
     * @return pagination envelope
     */
    PaginateResult<PermissionEntity> index(Map<String, String> filters);

    /**
     * Creates a new permission. Throws {@link com.nodeadmin.common.error.ConflictError}
     * when a permission with the same name already exists.
     *
     * @param request validated DTO
     * @return the persisted {@link PermissionEntity}
     */
    PermissionEntity store(PermissionRequest request);

    /**
     * Returns the permission with the given id for the edit form.
     *
     * @param id permission UUID
     * @return the {@link PermissionEntity}
     */
    PermissionEntity edit(String id);

    /**
     * Updates an existing permission. Throws {@link com.nodeadmin.common.error.ConflictError}
     * on duplicate name (excluding the record being updated).
     *
     * @param id      permission UUID
     * @param request validated DTO
     * @return the updated {@link PermissionEntity}
     */
    PermissionEntity update(String id, PermissionRequest request);

    /**
     * Hard-deletes the permission with the given id.
     *
     * @param id permission UUID
     */
    void delete(String id);

    /**
     * Hard-deletes all permissions whose ids appear in the provided list.
     *
     * @param ids list of permission UUIDs to remove
     */
    void deleteSelected(List<String> ids);

    /**
     * Idempotently upserts permissions from the application's registered routes.
     *
     * <p>For each {@link RouteDefinition} in the list:
     * <ul>
     *   <li>If no permission with {@code (name, method)} exists, one is created.</li>
     *   <li>If it already exists, it is left unchanged (idempotent).</li>
     * </ul>
     * {@code guardName} is derived from the route name: names starting with
     * {@code "api."} → {@code "api"}; everything else → {@code "web"}.
     *
     * <p>This method is called lazily when the Permission index page first loads
     * (via {@link com.nodeadmin.modules.access.permission.controller.web.v1.PermissionWebController}).
     *
     * @param routes list of all registered {@link RouteDefinition}s from {@link com.nodeadmin.common.route.RouteRegistry}
     */
    void syncFromRoutes(List<RouteDefinition> routes);
}

package com.nodeadmin.modules.access.role.service;

import com.nodeadmin.common.util.PaginateResult;
import com.nodeadmin.modules.access.role.dto.RoleRequest;
import com.nodeadmin.modules.access.role.entity.RoleEntity;

import java.util.List;
import java.util.Map;

/**
 * Service contract for the Role resource in the access module.
 *
 * <p>Mirrors NodeAdmin's {@code IRoleService}:
 * <ul>
 *   <li>CRUD: {@link #index}, {@link #store}, {@link #edit}, {@link #update},
 *       {@link #delete}, {@link #deleteSelected}</li>
 *   <li>Permission management: {@link #listPermissions}, {@link #assignPermission},
 *       {@link #unassignPermission}, {@link #assignSelected}, {@link #unassignSelected}</li>
 * </ul>
 *
 * <p>Supported filter keys for {@link #index}: {@code q_name}, {@code q_desc},
 * {@code q_status}, {@code q_guard}, {@code q_page}, {@code q_page_size}.
 *
 * <p>Supported filter keys for {@link #listPermissions}: {@code q_name},
 * {@code q_method}, {@code q_status} (Active = assigned only, Inactive = unassigned
 * only), {@code q_desc}, {@code q_page}, {@code q_page_size}.
 */
public interface IRoleService {

    /**
     * Returns a paginated list of roles, optionally filtered.
     *
     * @param filters raw request params with {@code q_*} prefix keys
     * @return pagination envelope
     */
    PaginateResult<RoleEntity> index(Map<String, String> filters);

    /**
     * Creates a new role. Throws {@link com.nodeadmin.common.error.ConflictError}
     * when a role with the same name already exists.
     *
     * @param request validated DTO
     * @return the persisted {@link RoleEntity}
     */
    RoleEntity store(RoleRequest request);

    /**
     * Returns the role with the given id for the edit form.
     *
     * @param id role UUID
     * @return the {@link RoleEntity}
     */
    RoleEntity edit(String id);

    /**
     * Updates an existing role. Throws {@link com.nodeadmin.common.error.ConflictError}
     * on duplicate name (excluding the record being updated).
     *
     * @param id      role UUID
     * @param request validated DTO
     * @return the updated {@link RoleEntity}
     */
    RoleEntity update(String id, RoleRequest request);

    /**
     * Hard-deletes the role with the given id.
     *
     * @param id role UUID
     */
    void delete(String id);

    /**
     * Hard-deletes all roles whose ids appear in the provided list.
     *
     * @param ids list of role UUIDs to remove
     */
    void deleteSelected(List<String> ids);

    /**
     * Returns a paginated list of ALL permissions with a computed {@code assigned}
     * flag indicating whether each permission is currently linked to the given role.
     * The result map also contains a {@code role} key ({@link RoleEntity}).
     *
     * @param roleId  role UUID
     * @param filters raw request params with {@code q_*} prefix keys
     * @return map with {@code data}, {@code total}, {@code page}, {@code pageSize},
     *         {@code totalPages}, and {@code role}
     */
    Map<String, Object> listPermissions(String roleId, Map<String, String> filters);

    /**
     * Assigns a single permission to a role (idempotent — no-op if already assigned).
     *
     * @param roleId role UUID
     * @param permId permission UUID
     */
    void assignPermission(String roleId, String permId);

    /**
     * Removes a single permission from a role.
     *
     * @param roleId role UUID
     * @param permId permission UUID
     */
    void unassignPermission(String roleId, String permId);

    /**
     * Assigns multiple permissions to a role in one operation (idempotent).
     *
     * @param roleId  role UUID
     * @param permIds list of permission UUIDs
     */
    void assignSelected(String roleId, List<String> permIds);

    /**
     * Removes multiple permissions from a role in one operation.
     *
     * @param roleId  role UUID
     * @param permIds list of permission UUIDs
     */
    void unassignSelected(String roleId, List<String> permIds);
}

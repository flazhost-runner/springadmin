package com.nodeadmin.modules.access.permission.repository;

import com.nodeadmin.modules.access.permission.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PermissionEntity}.
 *
 * <p>NodeAdmin's RBAC system identifies permissions by the combination of
 * {@code (name, method)} — a GET and a DELETE on the same logical route are
 * distinct permissions. The {@code findByNameAndMethod} and
 * {@code existsByNameAndMethod} methods mirror that lookup contract.
 */
@Repository
public interface PermissionRepository extends JpaRepository<PermissionEntity, String> {

    /**
     * Finds a permission by its route-name and HTTP method pair.
     *
     * @param name   the route name (e.g. {@code "admin.v1.access.user.index"})
     * @param method the HTTP method (e.g. {@code "GET"})
     * @return an {@link Optional} containing the matching permission, or empty
     */
    Optional<PermissionEntity> findByNameAndMethod(String name, String method);

    /**
     * Checks whether a permission with the given name+method combination exists.
     *
     * @param name   the route name
     * @param method the HTTP method
     * @return {@code true} if a matching record exists
     */
    boolean existsByNameAndMethod(String name, String method);

    /**
     * Returns all permissions belonging to a specific guard.
     *
     * @param guardName the guard identifier ({@code "web"} or {@code "api"})
     * @return list of permissions for that guard (may be empty, never null)
     */
    List<PermissionEntity> findByGuardName(String guardName);

    /**
     * Checks whether a role (by ID) has any permission for the given route name.
     * Used by {@code GlobalViewDataAdvice.HasAccessHelper} for Thymeleaf hasAccess() checks.
     *
     * @param route  the permission name / route key (e.g. {@code "admin.v1.access.user.index"})
     * @param roleId the role UUID
     * @return {@code true} if the role has at least one permission record matching the route name
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM permissions p " +
                   "JOIN roles_permissions rp ON rp.permission_id = p.id " +
                   "WHERE p.name = :route AND rp.role_id = :roleId",
           nativeQuery = true)
    boolean existsByRouteAndRoleId(@Param("route") String route, @Param("roleId") String roleId);

    /**
     * Checks whether any of the given roles (by name) has a permission for the given route name.
     * Fallback used by {@code GlobalViewDataAdvice.HasAccessHelper} when {@code roleId} is not
     * populated on the session user (e.g. sessions created before roleId was added).
     *
     * @param route     the permission name / route key
     * @param roleNames list of role names to check against
     * @return {@code true} if at least one matching role+permission record exists
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM permissions p " +
                   "JOIN roles_permissions rp ON rp.permission_id = p.id " +
                   "JOIN roles r ON r.id = rp.role_id " +
                   "WHERE p.name = :route AND r.name IN :roleNames",
           nativeQuery = true)
    boolean existsByRouteAndRoleNames(@Param("route") String route,
                                      @Param("roleNames") List<String> roleNames);
}

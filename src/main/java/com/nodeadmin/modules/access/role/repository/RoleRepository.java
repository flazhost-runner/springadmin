package com.nodeadmin.modules.access.role.repository;

import com.nodeadmin.modules.access.role.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link RoleEntity}.
 */
@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, String> {

    /**
     * Finds a role by its unique name.
     *
     * @param name the role name (e.g. {@code "Administrator"})
     * @return an {@link Optional} containing the role, or empty if not found
     */
    Optional<RoleEntity> findByName(String name);

    /**
     * Checks whether a role with the given name exists.
     *
     * @param name the role name to check
     * @return {@code true} if a record with that name exists
     */
    boolean existsByName(String name);

    /**
     * RBAC access check: returns {@code true} when at least one role whose name
     * is in {@code roleNames} has a linked permission matching both
     * {@code permissionName} and {@code permissionMethod}.
     *
     * <p>Used by {@link com.nodeadmin.config.security.AccessInterceptor} to verify
     * that a session user's roles grant access to a specific route.
     *
     * @param roleNames       collection of role names held by the current user
     * @param permissionName  the logical route name of the required permission
     * @param permissionMethod the HTTP method of the required permission (uppercase)
     * @return {@code true} if any of the given roles holds the specified permission
     */
    boolean existsByNameInAndPermissionsNameAndPermissionsMethod(
            Collection<String> roleNames,
            String permissionName,
            String permissionMethod);
}

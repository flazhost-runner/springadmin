package com.nodeadmin.common.route;

/**
 * Immutable descriptor for a single named route.
 *
 * <p>Mirrors NodeAdmin's route registration pattern where every route carries:
 * <ul>
 *   <li>{@code name}      — dot-separated logical identifier, e.g.
 *       {@code "admin.v1.access.user.index"}</li>
 *   <li>{@code method}    — uppercase HTTP verb: GET, POST, PUT, DELETE, PATCH</li>
 *   <li>{@code path}      — Ant-style URL pattern, e.g. {@code "/admin/v1/access/users"}</li>
 *   <li>{@code guardName} — {@code "web"} for session routes, {@code "api"} for JWT routes</li>
 * </ul>
 *
 * <p>Used by {@link RouteRegistry} for reverse-lookup (path + method → name)
 * and by {@link com.nodeadmin.config.security.AccessInterceptor} to resolve
 * the permission name for an incoming request.
 *
 * @param name      logical route name
 * @param method    HTTP verb (uppercase)
 * @param path      Ant-style path pattern
 * @param guardName guard identifier ({@code "web"} | {@code "api"})
 */
public record RouteDefinition(
        String name,
        String method,
        String path,
        String guardName
) {}

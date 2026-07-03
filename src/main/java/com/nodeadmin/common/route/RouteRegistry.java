package com.nodeadmin.common.route;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Central registry of all named routes in the application.
 *
 * <p>Each module registers its routes at startup (typically in a
 * {@code @PostConstruct} block or directly via its {@code @Configuration}).
 * The registry is used by {@link com.nodeadmin.config.security.AccessInterceptor}
 * to reverse-lookup the logical route name for an incoming HTTP request so that
 * RBAC permission checks can be performed by name+method.
 *
 * <p>Pattern matching is done with Spring's {@link AntPathMatcher} — the same
 * engine used by {@code RequestMappingHandlerMapping} — so wildcards like
 * {@code /admin/v1/access/users/{id}/**} resolve correctly.
 *
 * <p>This bean is a singleton managed by Spring; concurrent reads are safe
 * because the underlying list is only written during application startup
 * before any request arrives.
 */
@Component
public class RouteRegistry {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<RouteDefinition> routes = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers a single route definition.
     *
     * @param name      logical route name (dot-separated)
     * @param method    HTTP verb (uppercase)
     * @param path      Ant-style URL pattern
     * @param guardName {@code "web"} or {@code "api"}
     */
    public void register(String name, String method, String path, String guardName) {
        routes.add(new RouteDefinition(name, method.toUpperCase(), path, guardName));
    }

    /**
     * Convenience overload that infers {@code guardName} from the route name:
     * names starting with {@code "api."} get guard {@code "api"};
     * everything else gets {@code "web"}.
     *
     * @param name   logical route name
     * @param method HTTP verb (uppercase)
     * @param path   Ant-style URL pattern
     */
    public void register(String name, String method, String path) {
        String guardName = name.startsWith("api.") ? "api" : "web";
        register(name, method, path, guardName);
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Finds the first {@link RouteDefinition} whose {@code path} pattern matches
     * the given concrete {@code path} and whose {@code method} equals the given
     * HTTP verb (case-insensitive).
     *
     * <p>Routes are checked in registration order; the most specific pattern
     * should be registered first when ambiguity exists.
     *
     * @param path   the actual request URI (e.g. {@code "/admin/v1/access/users/abc-123"})
     * @param method the HTTP verb of the request (e.g. {@code "GET"})
     * @return an {@link Optional} containing the matching route, or empty if none matched
     */
    public Optional<RouteDefinition> findByPathAndMethod(String path, String method) {
        String upperMethod = method.toUpperCase();
        return routes.stream()
                .filter(r -> r.method().equalsIgnoreCase(upperMethod)
                        && pathMatcher.match(r.path(), path))
                .findFirst();
    }

    /**
     * Returns an unmodifiable snapshot of all registered routes.
     * Useful for diagnostics or permission seeding at startup.
     *
     * @return unmodifiable list of all {@link RouteDefinition}s
     */
    public List<RouteDefinition> all() {
        return List.copyOf(routes);
    }
}

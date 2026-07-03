package com.nodeadmin.config.security;

import com.nodeadmin.common.model.SessionUser;
import com.nodeadmin.common.route.RouteDefinition;
import com.nodeadmin.common.route.RouteRegistry;
import com.nodeadmin.common.util.FlashHelper;
import com.nodeadmin.modules.access.permission.repository.PermissionRepository;
import com.nodeadmin.modules.access.role.repository.RoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.List;
import java.util.Optional;

/**
 * Web (session-based) RBAC interceptor — mirrors NodeAdmin's
 * {@code access.middleware.ts} guard logic.
 *
 * <p>On denial: flashes {@code 'Unauthorized.'} and redirects to the Referer header
 * (falling back to dashboard) — matching NodeAdmin's accessMiddleware behaviour.
 */
@Component
public class AccessInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AccessInterceptor.class);

    private static final String LOGIN_URL     = "/auth/login";
    private static final String DASHBOARD_URL = "/admin/v1/dashboard";

    private final RouteRegistry        routeRegistry;
    private final PermissionRepository permissionRepository;
    private final RoleRepository       roleRepository;

    public AccessInterceptor(RouteRegistry routeRegistry,
                             PermissionRepository permissionRepository,
                             RoleRepository roleRepository) {
        this.routeRegistry        = routeRegistry;
        this.permissionRepository = permissionRepository;
        this.roleRepository       = roleRepository;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {

        HttpSession session = request.getSession(false);
        SessionUser sessionUser = (session != null)
                ? (SessionUser) session.getAttribute(FlashHelper.SESSION_USER)
                : null;

        // 1. Not authenticated — redirect to login
        if (sessionUser == null) {
            response.sendRedirect(request.getContextPath() + LOGIN_URL);
            return false;
        }

        // 2. Administrator bypass
        if (sessionUser.isAdministrator()) {
            return true;
        }

        // 3. Reverse-lookup route name
        String requestUri = request.getRequestURI();
        String method     = request.getMethod().toUpperCase();

        Optional<RouteDefinition> routeOpt =
                routeRegistry.findByPathAndMethod(requestUri, method);

        if (routeOpt.isEmpty()) {
            return true;
        }

        RouteDefinition route = routeOpt.get();

        // 4. Look up permission record — if not seeded, allow by default
        boolean permExists = permissionRepository.existsByNameAndMethod(route.name(), method);
        if (!permExists) {
            return true;
        }

        // 5. Check whether any of the user's roles grants this permission
        List<String> userRoleNames = sessionUser.getRoles();
        if (userRoleNames == null || userRoleNames.isEmpty()) {
            denyAccess(request, response, sessionUser.getEmail(), route.name(), method);
            return false;
        }

        boolean hasAccess = roleRepository
                .existsByNameInAndPermissionsNameAndPermissionsMethod(
                        userRoleNames, route.name(), method);

        if (!hasAccess) {
            denyAccess(request, response, sessionUser.getEmail(), route.name(), method);
            return false;
        }

        return true;
    }

    private void denyAccess(HttpServletRequest request,
                            HttpServletResponse response,
                            String userEmail,
                            String routeName,
                            String method) throws Exception {
        log.warn("Access denied: user={} route={} method={}", userEmail, routeName, method);

        // Flash 'Unauthorized.' — use Spring FlashMapManager so it survives the redirect
        FlashMap flashMap = new FlashMap();
        flashMap.put(FlashHelper.KEY_ERROR, "Unauthorized.");
        FlashMapManager flashMapManager = RequestContextUtils.getFlashMapManager(request);
        if (flashMapManager != null) {
            flashMapManager.saveOutputFlashMap(flashMap, request, response);
        }

        // Redirect to Referer, fall back to dashboard
        String referrer = request.getHeader("Referer");
        String redirectUrl = (referrer != null && !referrer.isBlank()) ? referrer : DASHBOARD_URL;
        response.sendRedirect(redirectUrl);
    }
}

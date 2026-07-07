package com.nodeadmin.common.advice;

import com.nodeadmin.common.model.SessionUser;
import com.nodeadmin.common.storage.StorageUrlBuilder;
import com.nodeadmin.common.util.FlashHelper;
import com.nodeadmin.modules.access.permission.repository.PermissionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Map;

/**
 * Injects Thymeleaf utility helpers into every controller's model so templates
 * can call {@code hasAccess()}, {@code hasRole()}, {@code getFile()},
 * {@code getOld()}, and {@code getError()} without controller boilerplate.
 *
 * <p>Mirrors NodeAdmin's {@code helper-view} middleware and the
 * {@code hasAccess} / {@code hasRole} template globals.
 *
 * <p>Usage in Thymeleaf:
 * <pre>
 * th:if="${hasAccessHelper.check('admin.v1.access.user.index')}"
 * th:if="${hasRoleHelper.check('Administrator')}"
 * th:value="${getOldHelper.get('email')}"
 * th:text="${getErrorHelper.get('email')}"
 * </pre>
 */
@ControllerAdvice
public class GlobalViewDataAdvice {

    private final PermissionRepository permissionRepository;
    private final StorageUrlBuilder    storageUrlBuilder;

    public GlobalViewDataAdvice(PermissionRepository permissionRepository,
                                StorageUrlBuilder storageUrlBuilder) {
        this.permissionRepository = permissionRepository;
        this.storageUrlBuilder    = storageUrlBuilder;
    }

    @ModelAttribute
    public void addGlobalAttributes(Model model,
                                    HttpServletRequest request,
                                    HttpSession session) {
        model.addAttribute("hasAccessHelper", new HasAccessHelper(session, permissionRepository));
        model.addAttribute("hasRoleHelper",   new HasRoleHelper(session));
        model.addAttribute("getFileHelper",   new GetFileHelper(request));
        model.addAttribute("getOldHelper",    new GetOldHelper(session));
        model.addAttribute("getErrorHelper",  new GetErrorHelper(session));
        // Driver-aware storage render URL: ${storageUrl.url(key)} in Thymeleaf.
        // local → /public/storage/<key>; oss/s3 → absolute cloud URL. Property decides.
        model.addAttribute("storageUrl",       storageUrlBuilder);
    }

    // =========================================================================
    // hasAccess(route) — mirrors NodeAdmin's hasAccess() template global
    // =========================================================================

    public static class HasAccessHelper {
        private final HttpSession          session;
        private final PermissionRepository permissionRepo;

        public HasAccessHelper(HttpSession session, PermissionRepository permissionRepo) {
            this.session        = session;
            this.permissionRepo = permissionRepo;
        }

        /**
         * Returns {@code true} if the session user may access the given route name.
         *
         * <p>Check order:
         * <ol>
         *   <li>No session user → {@code false}.</li>
         *   <li>Administrator role → {@code true} (bypass).</li>
         *   <li>roleId populated → query via {@code existsByRouteAndRoleId}.</li>
         *   <li>roleId absent (legacy session) → query via {@code existsByRouteAndRoleNames}.</li>
         * </ol>
         *
         * @param route permission name / route key, e.g. {@code "admin.v1.access.user.index"}
         */
        public boolean check(String route) {
            SessionUser user = (SessionUser) session.getAttribute(FlashHelper.SESSION_USER);
            if (user == null) return false;
            if (user.isAdministrator()) return true;

            String roleId = user.getRoleId();
            if (roleId != null && !roleId.isBlank()) {
                return permissionRepo.existsByRouteAndRoleId(route, roleId);
            }

            // Fallback: use role names list (sessions without roleId populated)
            List<String> roleNames = user.getRoles();
            if (roleNames == null || roleNames.isEmpty()) return false;
            return permissionRepo.existsByRouteAndRoleNames(route, roleNames);
        }
    }

    // =========================================================================
    // hasRole(roleName) — mirrors NodeAdmin's hasRole() template global
    // =========================================================================

    public static class HasRoleHelper {
        private final HttpSession session;

        public HasRoleHelper(HttpSession session) {
            this.session = session;
        }

        /**
         * Returns {@code true} if the session user carries the given role name.
         *
         * @param roleName role to check, e.g. {@code "Administrator"}
         */
        public boolean check(String roleName) {
            SessionUser user = (SessionUser) session.getAttribute(FlashHelper.SESSION_USER);
            if (user == null) return false;
            // Check primary roleName field first; fall back to roles list
            if (roleName.equals(user.getRoleName())) return true;
            List<String> roles = user.getRoles();
            return roles != null && roles.contains(roleName);
        }
    }

    // =========================================================================
    // getFile(name) — returns a request-scoped uploaded file path
    // =========================================================================

    public static class GetFileHelper {
        private final HttpServletRequest request;

        public GetFileHelper(HttpServletRequest request) {
            this.request = request;
        }

        /**
         * Returns a request-attribute file path set by the controller, or {@code ""}.
         *
         * @param name attribute name (without the {@code "files_"} prefix)
         */
        public String get(String name) {
            Object f = request.getAttribute("files_" + name);
            return f != null ? f.toString() : "";
        }
    }

    // =========================================================================
    // getOld(key) — mirrors NodeAdmin's req.flash('_old')[key]
    // =========================================================================

    public static class GetOldHelper {
        private final HttpSession session;

        public GetOldHelper(HttpSession session) {
            this.session = session;
        }

        /**
         * Returns the previously submitted form value for {@code key}, or {@code ""}.
         * Values are stored under {@link FlashHelper#KEY_OLD} ({@code "flash_old"}).
         *
         * @param key form field name
         */
        @SuppressWarnings("unchecked")
        public String get(String key) {
            Map<String, String> old = (Map<String, String>) session.getAttribute(FlashHelper.KEY_OLD);
            return old != null ? old.getOrDefault(key, "") : "";
        }
    }

    // =========================================================================
    // getError(key) — mirrors NodeAdmin's req.flash('_errors')[key]
    // =========================================================================

    public static class GetErrorHelper {
        private final HttpSession session;

        public GetErrorHelper(HttpSession session) {
            this.session = session;
        }

        /**
         * Returns the per-field validation error for {@code key}, or {@code ""}.
         * Values are stored under {@link FlashHelper#KEY_ERRORS} ({@code "flash_errors"}).
         *
         * @param key form field name
         */
        @SuppressWarnings("unchecked")
        public String get(String key) {
            Map<String, String> errors =
                    (Map<String, String>) session.getAttribute(FlashHelper.KEY_ERRORS);
            return errors != null ? errors.getOrDefault(key, "") : "";
        }
    }
}

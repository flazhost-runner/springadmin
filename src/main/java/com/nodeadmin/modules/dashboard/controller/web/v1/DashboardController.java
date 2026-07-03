package com.nodeadmin.modules.dashboard.controller.web.v1;

import com.nodeadmin.common.route.RouteRegistry;
import com.nodeadmin.modules.access.permission.repository.PermissionRepository;
import com.nodeadmin.modules.access.role.repository.RoleRepository;
import com.nodeadmin.modules.access.user.repository.UserRepository;
import com.nodeadmin.modules.setting.entity.SettingEntity;
import com.nodeadmin.modules.setting.repository.SettingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Web controller for the Dashboard module.
 *
 * <p>Route names follow NodeAdmin's {@code namedRoutes} convention:
 * <ul>
 *   <li>{@code admin.v1.dashboard.index} — GET /admin/v1/dashboard</li>
 * </ul>
 *
 * <p>Model attributes injected into the view:
 * <ul>
 *   <li>{@code userCount}       — total user count</li>
 *   <li>{@code roleCount}       — total role count</li>
 *   <li>{@code permissionCount} — total permission count</li>
 *   <li>{@code activeThemeName} — active theme name from {@link SettingEntity}</li>
 * </ul>
 */
@Controller
@RequestMapping("/admin/v1/dashboard")
public class DashboardController {

    private final UserRepository       userRepository;
    private final RoleRepository       roleRepository;
    private final PermissionRepository permissionRepository;
    private final SettingRepository    settingRepository;
    private final RouteRegistry        routeRegistry;

    public DashboardController(UserRepository userRepository,
                               RoleRepository roleRepository,
                               PermissionRepository permissionRepository,
                               SettingRepository settingRepository,
                               RouteRegistry routeRegistry) {
        this.userRepository       = userRepository;
        this.roleRepository       = roleRepository;
        this.permissionRepository = permissionRepository;
        this.settingRepository    = settingRepository;
        this.routeRegistry        = routeRegistry;
    }

    @PostConstruct
    public void registerRoutes() {
        routeRegistry.register("admin.v1.dashboard.index", "GET", "/admin/v1/dashboard");
    }

    // =========================================================================
    // Index — GET /admin/v1/dashboard
    // =========================================================================

    @GetMapping
    public String index(Model model) {
        long userCount       = userRepository.count();
        long roleCount       = roleRepository.count();
        long permissionCount = permissionRepository.count();

        String activeThemeName = settingRepository.findFirst()
                .map(SettingEntity::getTheme)
                .orElse("Blue");

        model.addAttribute("userCount",       userCount);
        model.addAttribute("roleCount",       roleCount);
        model.addAttribute("permissionCount", permissionCount);
        model.addAttribute("activeThemeName", activeThemeName);

        return "modules/dashboard/index";
    }
}

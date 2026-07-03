package com.nodeadmin.modules.components.controller.web.v1;

import com.nodeadmin.common.route.RouteRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Web controller for the UI Components showcase module.
 *
 * <p>Route names follow NodeAdmin's {@code namedRoutes} convention:
 * <ul>
 *   <li>{@code admin.v1.components.index} — GET /admin/v1/components</li>
 * </ul>
 *
 * <p>This controller is read-only; it renders a static showcase template
 * with no dynamic model data (all samples are hard-coded in the template,
 * mirroring the NodeAdmin components view).
 */
@Controller
@RequestMapping("/admin/v1/components")
public class ComponentsController {

    private final RouteRegistry routeRegistry;

    public ComponentsController(RouteRegistry routeRegistry) {
        this.routeRegistry = routeRegistry;
    }

    @PostConstruct
    public void registerRoutes() {
        routeRegistry.register("admin.v1.components.index", "GET", "/admin/v1/components");
    }

    // =========================================================================
    // Index — GET /admin/v1/components
    // =========================================================================

    @GetMapping
    public String index() {
        return "modules/components/index";
    }
}

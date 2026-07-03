package com.nodeadmin.modules.access.permission.controller.web.v1;

import com.nodeadmin.common.route.RouteRegistry;
import com.nodeadmin.common.util.FlashHelper;
import com.nodeadmin.modules.access.permission.dto.PermissionRequest;
import com.nodeadmin.modules.access.permission.service.IPermissionService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Web (session-based) controller for the Permission resource.
 *
 * <p>Route names:
 * <ul>
 *   <li>{@code admin.v1.access.permission.index}          — GET    /admin/v1/access/permissions</li>
 *   <li>{@code admin.v1.access.permission.create}         — GET    /admin/v1/access/permissions/create</li>
 *   <li>{@code admin.v1.access.permission.store}          — POST   /admin/v1/access/permissions</li>
 *   <li>{@code admin.v1.access.permission.edit}           — GET    /admin/v1/access/permissions/{id}/edit</li>
 *   <li>{@code admin.v1.access.permission.update}         — PUT    /admin/v1/access/permissions/{id}</li>
 *   <li>{@code admin.v1.access.permission.delete}         — DELETE /admin/v1/access/permissions/{id}</li>
 *   <li>{@code admin.v1.access.permission.delete-selected}— POST   /admin/v1/access/permissions/delete-selected</li>
 * </ul>
 *
 * <p>{@link IPermissionService#syncFromRoutes} is called on every index load to
 * lazily seed any newly registered routes as permissions (mirrors NodeAdmin's
 * {@code getAllRegisteredRoute} in PermissionService).
 */
@Controller
@RequestMapping("/admin/v1/access/permissions")
public class PermissionWebController {

    private static final String REDIRECT_INDEX = "redirect:/admin/v1/access/permissions";

    private final IPermissionService permissionService;
    private final RouteRegistry      routeRegistry;

    public PermissionWebController(IPermissionService permissionService,
                                   RouteRegistry routeRegistry) {
        this.permissionService = permissionService;
        this.routeRegistry     = routeRegistry;
    }

    @PostConstruct
    public void registerRoutes() {
        routeRegistry.register("admin.v1.access.permission.index",          "GET",    "/admin/v1/access/permissions");
        routeRegistry.register("admin.v1.access.permission.create",         "GET",    "/admin/v1/access/permissions/create");
        routeRegistry.register("admin.v1.access.permission.store",          "POST",   "/admin/v1/access/permissions");
        routeRegistry.register("admin.v1.access.permission.edit",           "GET",    "/admin/v1/access/permissions/{id}/edit");
        routeRegistry.register("admin.v1.access.permission.update",         "PUT",    "/admin/v1/access/permissions/{id}");
        routeRegistry.register("admin.v1.access.permission.delete",         "DELETE", "/admin/v1/access/permissions/{id}");
        routeRegistry.register("admin.v1.access.permission.delete-selected","POST",   "/admin/v1/access/permissions/delete-selected");
    }

    // =========================================================================
    // Index — GET /admin/v1/access/permissions
    // =========================================================================

    /**
     * Lazily syncs registered routes to the permissions table before rendering.
     * Mirrors NodeAdmin's PermissionService.getAllRegisteredRoute() call in the
     * permission index handler.
     */
    @GetMapping
    public String index(@RequestParam Map<String, String> filters, Model model) {
        // Lazy sync: upsert any new routes as permissions
        permissionService.syncFromRoutes(routeRegistry.all());

        var page = permissionService.index(filters);
        model.addAttribute("datas",    page.data());
        model.addAttribute("paginate", Map.of(
                "currentPage", page.page(),
                "totalPage",   page.totalPages(),
                "pageSize",    page.pageSize()
        ));
        model.addAttribute("filter", filters);
        return "modules/access/permissions/index";
    }

    // =========================================================================
    // Create — GET /admin/v1/access/permissions/create
    // =========================================================================

    @GetMapping("/create")
    public String create(Model model) {
        model.addAttribute("form", new PermissionRequest());
        return "modules/access/permissions/create";
    }

    // =========================================================================
    // Store — POST /admin/v1/access/permissions/store
    // =========================================================================

    @PostMapping
    public String store(@Valid @ModelAttribute("permissionRequest") PermissionRequest permissionRequest,
                        BindingResult bindingResult,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(FlashHelper.KEY_ERROR,
                    bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/v1/access/permissions/create";
        }
        permissionService.store(permissionRequest);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Create Permission Success.");
        return REDIRECT_INDEX;
    }

    // =========================================================================
    // Edit — GET /admin/v1/access/permissions/{id}/edit
    // =========================================================================

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable String id, Model model) {
        model.addAttribute("data", permissionService.edit(id));
        model.addAttribute("form", new PermissionRequest());
        return "modules/access/permissions/edit";
    }

    // =========================================================================
    // Update — PUT /admin/v1/access/permissions/{id}/update
    // =========================================================================

    @PutMapping("/{id}")
    public String update(@PathVariable String id,
                         @Valid @ModelAttribute("permissionRequest") PermissionRequest permissionRequest,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(FlashHelper.KEY_ERROR,
                    bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/v1/access/permissions/" + id + "/edit";
        }
        permissionService.update(id, permissionRequest);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Update Permission Success.");
        return REDIRECT_INDEX;
    }

    // =========================================================================
    // Delete — DELETE /admin/v1/access/permissions/{id}/delete
    // =========================================================================

    @DeleteMapping("/{id}")
    public String delete(@PathVariable String id, RedirectAttributes redirectAttributes) {
        permissionService.delete(id);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Delete Permission Success.");
        return REDIRECT_INDEX;
    }

    // =========================================================================
    // Delete Selected — POST /admin/v1/access/permissions/delete-selected
    // =========================================================================

    @PostMapping("/delete-selected")
    public String deleteSelected(@RequestParam(value = "selected") List<String> ids,
                                 RedirectAttributes redirectAttributes) {
        permissionService.deleteSelected(ids);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Delete Permission Success.");
        return REDIRECT_INDEX;
    }
}

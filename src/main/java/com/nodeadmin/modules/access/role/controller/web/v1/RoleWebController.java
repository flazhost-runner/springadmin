package com.nodeadmin.modules.access.role.controller.web.v1;

import com.nodeadmin.common.route.RouteRegistry;
import com.nodeadmin.common.util.FlashHelper;
import com.nodeadmin.modules.access.role.dto.RoleRequest;
import com.nodeadmin.modules.access.role.service.IRoleService;
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
 * Web (session-based) controller for the Role resource.
 *
 * <p>Route names:
 * <ul>
 *   <li>{@code admin.v1.access.role.index}                    — GET    /admin/v1/access/roles</li>
 *   <li>{@code admin.v1.access.role.create}                   — GET    /admin/v1/access/roles/create</li>
 *   <li>{@code admin.v1.access.role.store}                    — POST   /admin/v1/access/roles</li>
 *   <li>{@code admin.v1.access.role.edit}                     — GET    /admin/v1/access/roles/{id}/edit</li>
 *   <li>{@code admin.v1.access.role.update}                   — PUT    /admin/v1/access/roles/{id}</li>
 *   <li>{@code admin.v1.access.role.delete}                   — DELETE /admin/v1/access/roles/{id}</li>
 *   <li>{@code admin.v1.access.role.delete-selected}          — POST   /admin/v1/access/roles/delete-selected</li>
 *   <li>{@code admin.v1.access.role.permission}               — GET    /admin/v1/access/roles/{id}/permission</li>
 *   <li>{@code admin.v1.access.role.permission.assign}        — GET    /admin/v1/access/roles/{id}/permission/{permId}/assign</li>
 *   <li>{@code admin.v1.access.role.permission.assign_selected}— POST  /admin/v1/access/roles/{id}/permission/assign_selected</li>
 *   <li>{@code admin.v1.access.role.permission.unassign}      — GET    /admin/v1/access/roles/{id}/permission/{permId}/unassign</li>
 *   <li>{@code admin.v1.access.role.permission.unassign_selected}— POST /admin/v1/access/roles/{id}/permission/unassign_selected</li>
 * </ul>
 */
@Controller
@RequestMapping("/admin/v1/access/roles")
public class RoleWebController {

    private static final String REDIRECT_INDEX = "redirect:/admin/v1/access/roles";

    private final IRoleService  roleService;
    private final RouteRegistry routeRegistry;

    public RoleWebController(IRoleService roleService, RouteRegistry routeRegistry) {
        this.roleService   = roleService;
        this.routeRegistry = routeRegistry;
    }

    @PostConstruct
    public void registerRoutes() {
        routeRegistry.register("admin.v1.access.role.index",                     "GET",    "/admin/v1/access/roles");
        routeRegistry.register("admin.v1.access.role.create",                    "GET",    "/admin/v1/access/roles/create");
        routeRegistry.register("admin.v1.access.role.store",                     "POST",   "/admin/v1/access/roles/store");
        routeRegistry.register("admin.v1.access.role.edit",                      "GET",    "/admin/v1/access/roles/{id}/edit");
        routeRegistry.register("admin.v1.access.role.update",                    "PUT",    "/admin/v1/access/roles/{id}/update");
        routeRegistry.register("admin.v1.access.role.delete",                    "DELETE", "/admin/v1/access/roles/{id}/delete");
        routeRegistry.register("admin.v1.access.role.delete-selected",           "POST",   "/admin/v1/access/roles/delete_selected");
        routeRegistry.register("admin.v1.access.role.permission",                "GET",    "/admin/v1/access/roles/{id}/permission");
        routeRegistry.register("admin.v1.access.role.permission.assign",         "GET",    "/admin/v1/access/roles/{id}/permission/{permId}/assign");
        routeRegistry.register("admin.v1.access.role.permission.assign_selected","POST",   "/admin/v1/access/roles/{id}/permission/assign_selected");
        routeRegistry.register("admin.v1.access.role.permission.unassign",       "GET",    "/admin/v1/access/roles/{id}/permission/{permId}/unassign");
        routeRegistry.register("admin.v1.access.role.permission.unassign_selected","POST", "/admin/v1/access/roles/{id}/permission/unassign_selected");
    }

    // =========================================================================
    // Index — GET /admin/v1/access/roles
    // =========================================================================

    @GetMapping
    public String index(@RequestParam Map<String, String> filters, Model model) {
        var page = roleService.index(filters);
        model.addAttribute("datas",    page.data());
        model.addAttribute("paginate", Map.of(
                "currentPage", page.page(),
                "totalPage",   page.totalPages(),
                "pageSize",    page.pageSize()
        ));
        model.addAttribute("filter", filters);
        return "modules/access/roles/index";
    }

    // =========================================================================
    // Create — GET /admin/v1/access/roles/create
    // =========================================================================

    @GetMapping("/create")
    public String create(Model model) {
        model.addAttribute("form", new RoleRequest());
        return "modules/access/roles/create";
    }

    // =========================================================================
    // Store — POST /admin/v1/access/roles/store (also /admin/v1/access/roles)
    // =========================================================================

    @PostMapping({"/store", ""})
    public String store(@Valid @ModelAttribute("roleRequest") RoleRequest roleRequest,
                        BindingResult bindingResult,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(FlashHelper.KEY_ERROR,
                    bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/v1/access/roles/create";
        }
        roleService.store(roleRequest);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Create Role Success.");
        return REDIRECT_INDEX;
    }

    // =========================================================================
    // Edit — GET /admin/v1/access/roles/{id}/edit
    // =========================================================================

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable String id, Model model) {
        model.addAttribute("data", roleService.edit(id));
        model.addAttribute("form", new RoleRequest());
        return "modules/access/roles/edit";
    }

    // =========================================================================
    // Update — PUT /admin/v1/access/roles/{id}/update
    // =========================================================================

    @PutMapping({"/{id}/update", "/{id}"})
    public String update(@PathVariable String id,
                         @Valid @ModelAttribute("roleRequest") RoleRequest roleRequest,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(FlashHelper.KEY_ERROR,
                    bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/v1/access/roles/" + id + "/edit";
        }
        roleService.update(id, roleRequest);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Update Role Success.");
        return REDIRECT_INDEX;
    }

    // =========================================================================
    // Delete — DELETE /admin/v1/access/roles/{id}/delete
    // =========================================================================

    @DeleteMapping({"/{id}/delete", "/{id}"})
    public String delete(@PathVariable String id, RedirectAttributes redirectAttributes) {
        roleService.delete(id);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Delete Role Success.");
        return REDIRECT_INDEX;
    }

    // =========================================================================
    // Delete Selected — POST /admin/v1/access/roles/delete-selected
    // =========================================================================

    @PostMapping({"/delete_selected", "/delete-selected"})
    public String deleteSelected(@RequestParam(value = "selected") List<String> ids,
                                 RedirectAttributes redirectAttributes) {
        roleService.deleteSelected(ids);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Delete Role Success.");
        return REDIRECT_INDEX;
    }

    // =========================================================================
    // Permission — GET /admin/v1/access/roles/{id}/permission
    // =========================================================================

    @GetMapping({"/{id}/permission", "/{id}/permissions"})
    public String permission(@PathVariable String id,
                             @RequestParam Map<String, String> filters,
                             Model model) {
        Map<String, Object> result = roleService.listPermissions(id, filters);
        model.addAttribute("permissions", result.get("data"));
        model.addAttribute("paginate", Map.of(
                "currentPage", result.get("page"),
                "totalPage",   result.get("totalPages"),
                "pageSize",    result.get("pageSize")
        ));
        model.addAttribute("role",   result.get("role"));
        model.addAttribute("filter", filters);
        return "modules/access/roles/permission";
    }

    // =========================================================================
    // Assign Permission — GET /admin/v1/access/roles/{id}/permission/{permId}/assign
    // =========================================================================

    @GetMapping("/{id}/permission/{permId}/assign")
    public String assignPermission(@PathVariable String id,
                                   @PathVariable String permId,
                                   RedirectAttributes redirectAttributes) {
        roleService.assignPermission(id, permId);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Assign Permission Success.");
        return "redirect:/admin/v1/access/roles/" + id + "/permission";
    }

    // =========================================================================
    // Assign Selected — POST /admin/v1/access/roles/{id}/permission/assign_selected
    // =========================================================================

    @PostMapping("/{id}/permission/assign_selected")
    public String assignSelected(@PathVariable String id,
                                 @RequestParam(value = "selected") List<String> permIds,
                                 RedirectAttributes redirectAttributes) {
        roleService.assignSelected(id, permIds);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Assign Permission Success.");
        return "redirect:/admin/v1/access/roles/" + id + "/permission";
    }

    // =========================================================================
    // Unassign Permission — GET /admin/v1/access/roles/{id}/permission/{permId}/unassign
    // =========================================================================

    @GetMapping("/{id}/permission/{permId}/unassign")
    public String unassignPermission(@PathVariable String id,
                                     @PathVariable String permId,
                                     RedirectAttributes redirectAttributes) {
        roleService.unassignPermission(id, permId);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Unassign Permission Success.");
        return "redirect:/admin/v1/access/roles/" + id + "/permission";
    }

    // =========================================================================
    // Unassign Selected — POST /admin/v1/access/roles/{id}/permission/unassign_selected
    // =========================================================================

    @PostMapping("/{id}/permission/unassign_selected")
    public String unassignSelected(@PathVariable String id,
                                   @RequestParam(value = "selected") List<String> permIds,
                                   RedirectAttributes redirectAttributes) {
        roleService.unassignSelected(id, permIds);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Unassign Permission Success.");
        return "redirect:/admin/v1/access/roles/" + id + "/permission";
    }
}

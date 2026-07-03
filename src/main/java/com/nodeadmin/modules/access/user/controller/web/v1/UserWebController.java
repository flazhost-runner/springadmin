package com.nodeadmin.modules.access.user.controller.web.v1;

import com.nodeadmin.common.model.SessionUser;
import com.nodeadmin.common.route.RouteRegistry;
import com.nodeadmin.common.util.FlashHelper;
import com.nodeadmin.modules.access.user.dto.UserRequest;
import com.nodeadmin.modules.access.user.entity.UserEntity;
import com.nodeadmin.modules.access.user.service.IUserService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Web (session-based) controller for the User resource.
 *
 * <p>Route names follow NodeAdmin's {@code namedRoutes} convention:
 * <ul>
 *   <li>{@code admin.v1.access.user.index}          — GET    /admin/v1/access/users</li>
 *   <li>{@code admin.v1.access.user.create}         — GET    /admin/v1/access/users/create</li>
 *   <li>{@code admin.v1.access.user.store}          — POST   /admin/v1/access/users</li>
 *   <li>{@code admin.v1.access.user.edit}           — GET    /admin/v1/access/users/{id}/edit</li>
 *   <li>{@code admin.v1.access.user.update}         — PUT    /admin/v1/access/users/{id}</li>
 *   <li>{@code admin.v1.access.user.delete}         — DELETE /admin/v1/access/users/{id}</li>
 *   <li>{@code admin.v1.access.user.delete-selected}— POST   /admin/v1/access/users/delete-selected</li>
 * </ul>
 *
 * <p>All routes are registered with {@link RouteRegistry} in {@link #registerRoutes()}.
 * Flash messages use {@code flashKey} (success|error) and {@code flashMessage}.
 */
@Controller
@RequestMapping("/admin/v1/access/users")
public class UserWebController {

    private static final String REDIRECT_INDEX = "redirect:/admin/v1/access/users";

    private final IUserService  userService;
    private final RouteRegistry routeRegistry;

    public UserWebController(IUserService userService, RouteRegistry routeRegistry) {
        this.userService   = userService;
        this.routeRegistry = routeRegistry;
    }

    @PostConstruct
    public void registerRoutes() {
        routeRegistry.register("admin.v1.access.user.index",          "GET",    "/admin/v1/access/users");
        routeRegistry.register("admin.v1.access.user.create",         "GET",    "/admin/v1/access/users/create");
        routeRegistry.register("admin.v1.access.user.store",          "POST",   "/admin/v1/access/users");
        routeRegistry.register("admin.v1.access.user.edit",           "GET",    "/admin/v1/access/users/{id}/edit");
        routeRegistry.register("admin.v1.access.user.update",         "PUT",    "/admin/v1/access/users/{id}");
        routeRegistry.register("admin.v1.access.user.delete",         "DELETE", "/admin/v1/access/users/{id}");
        routeRegistry.register("admin.v1.access.user.delete-selected","POST",   "/admin/v1/access/users/delete-selected");
    }

    // =========================================================================
    // Index — GET /admin/v1/access/users
    // =========================================================================

    @GetMapping
    public String index(@RequestParam Map<String, String> filters, Model model) {
        Map<String, Object> result = userService.index(filters);
        model.addAttribute("datas",    result.get("data"));
        model.addAttribute("paginate", Map.of(
                "currentPage", result.get("page"),
                "totalPage",   result.get("totalPages"),
                "pageSize",    result.get("pageSize")
        ));
        model.addAttribute("roles",  result.get("roles"));
        model.addAttribute("filter", filters);
        return "modules/access/users/index";
    }

    // =========================================================================
    // Create — GET /admin/v1/access/users/create
    // =========================================================================

    @GetMapping("/create")
    public String create(Model model) {
        Map<String, Object> result = userService.index(Map.of());
        model.addAttribute("roles",     result.get("roles"));
        model.addAttribute("timezones", sortedTimezones());
        model.addAttribute("form",      new UserRequest());
        return "modules/access/users/create";
    }

    // =========================================================================
    // Store — POST /admin/v1/access/users/store
    // =========================================================================

    @PostMapping
    public String store(@Valid @ModelAttribute("userRequest") UserRequest userRequest,
                        BindingResult bindingResult,
                        @RequestPart(value = "picture", required = false) MultipartFile[] files,
                        @RequestParam(value = "roles[]", required = false) List<String> rolesFromBrackets,
                        @RequestParam(value = "blocked_reason", required = false) String blockedReasonParam,
                        HttpSession session,
                        RedirectAttributes redirectAttributes,
                        Model model) {
        if (rolesFromBrackets != null) userRequest.setRoles(rolesFromBrackets);
        if (blockedReasonParam != null) userRequest.setBlockedReason(blockedReasonParam);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(FlashHelper.KEY_ERROR,
                    bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/v1/access/users/create";
        }
        SessionUser actor = (SessionUser) session.getAttribute(FlashHelper.SESSION_USER);
        String createdBy = (actor != null) ? actor.getId() : "system";
        userService.store(userRequest, createdBy, files);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Create User Success.");
        return REDIRECT_INDEX;
    }

    // =========================================================================
    // Edit — GET /admin/v1/access/users/{id}/edit
    // =========================================================================

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable String id, Model model) {
        Map<String, Object> result = userService.edit(id);
        model.addAttribute("data",      result.get("data"));
        model.addAttribute("roles",     result.get("roles"));
        model.addAttribute("timezones", sortedTimezones());
        model.addAttribute("form",      new UserRequest());
        return "modules/access/users/edit";
    }

    // =========================================================================
    // Update — PUT /admin/v1/access/users/{id}/update
    // =========================================================================

    @PutMapping("/{id}")
    public String update(@PathVariable String id,
                         @Valid @ModelAttribute("userRequest") UserRequest userRequest,
                         BindingResult bindingResult,
                         @RequestPart(value = "picture", required = false) MultipartFile[] files,
                         @RequestParam(value = "roles[]", required = false) List<String> rolesFromBrackets,
                         @RequestParam(value = "blocked_reason", required = false) String blockedReasonParam,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        if (rolesFromBrackets != null) userRequest.setRoles(rolesFromBrackets);
        if (blockedReasonParam != null) userRequest.setBlockedReason(blockedReasonParam);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(FlashHelper.KEY_ERROR,
                    bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/admin/v1/access/users/" + id + "/edit";
        }
        SessionUser actor = (SessionUser) session.getAttribute(FlashHelper.SESSION_USER);
        String updatedBy = (actor != null) ? actor.getId() : "system";
        userService.update(id, userRequest, updatedBy, files);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Update User Success.");
        return REDIRECT_INDEX;
    }

    // =========================================================================
    // Delete — DELETE /admin/v1/access/users/{id}/delete
    // =========================================================================

    @DeleteMapping("/{id}")
    public String delete(@PathVariable String id, RedirectAttributes redirectAttributes) {
        userService.delete(id);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Delete User Success.");
        return REDIRECT_INDEX;
    }

    // =========================================================================
    // Delete Selected — POST /admin/v1/access/users/delete-selected
    // =========================================================================

    private List<String> sortedTimezones() {
        List<String> zones = new ArrayList<>(ZoneId.getAvailableZoneIds());
        Collections.sort(zones);
        return zones;
    }

    @PostMapping("/delete-selected")
    public String deleteSelected(@RequestParam(value = "selected") List<String> ids,
                                 RedirectAttributes redirectAttributes) {
        userService.deleteSelected(ids);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Delete User Success.");
        return REDIRECT_INDEX;
    }
}

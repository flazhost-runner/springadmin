package com.nodeadmin.modules.profile.controller.web.v1;

import com.nodeadmin.common.model.SessionUser;
import com.nodeadmin.common.route.RouteRegistry;
import com.nodeadmin.common.util.FlashHelper;
import com.nodeadmin.modules.access.user.entity.UserEntity;
import com.nodeadmin.modules.profile.dto.ProfileRequest;
import com.nodeadmin.modules.profile.service.IProfileService;
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

/**
 * Web (session-based) controller for the Profile module.
 *
 * <p>Route names follow NodeAdmin's {@code namedRoutes} convention:
 * <ul>
 *   <li>{@code admin.v1.profile.index}  — GET /admin/v1/profile</li>
 *   <li>{@code admin.v1.profile.update} — PUT /admin/v1/profile/update</li>
 * </ul>
 *
 * <p>Profile updates only touch profile-safe fields (code, name, phone, email,
 * timezone, password, status, picture). Roles are never modified here.
 */
@Controller
@RequestMapping("/admin/v1/profile")
public class ProfileController {

    private static final String REDIRECT_PROFILE = "redirect:/admin/v1/profile";

    private final IProfileService profileService;
    private final RouteRegistry   routeRegistry;

    public ProfileController(IProfileService profileService,
                             RouteRegistry routeRegistry) {
        this.profileService = profileService;
        this.routeRegistry  = routeRegistry;
    }

    @PostConstruct
    public void registerRoutes() {
        routeRegistry.register("admin.v1.profile.index",  "GET", "/admin/v1/profile");
        routeRegistry.register("admin.v1.profile.update", "PUT", "/admin/v1/profile/update");
    }

    // =========================================================================
    // Index — GET /admin/v1/profile
    // =========================================================================

    @GetMapping
    public String index(HttpSession session, Model model) {
        SessionUser sessionUser = (SessionUser) session.getAttribute(FlashHelper.SESSION_USER);
        if (sessionUser == null) {
            return "redirect:/auth/login";
        }

        UserEntity data = profileService.getProfile(sessionUser.getId());

        model.addAttribute("data",      data);
        model.addAttribute("timezones", getTimezones());
        model.addAttribute("profileRequest", new ProfileRequest());

        return "modules/profile/profile";
    }

    // =========================================================================
    // Update — PUT /admin/v1/profile/update
    // =========================================================================

    @PutMapping("/update")
    public String update(@Valid @ModelAttribute("profileRequest") ProfileRequest profileRequest,
                         BindingResult bindingResult,
                         @RequestPart(value = "picture", required = false) MultipartFile picture,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(FlashHelper.KEY_ERROR,
                    bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return REDIRECT_PROFILE;
        }

        SessionUser sessionUser = (SessionUser) session.getAttribute(FlashHelper.SESSION_USER);
        if (sessionUser == null) {
            return "redirect:/auth/login";
        }

        profileService.updateProfile(sessionUser.getId(), profileRequest, picture);

        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Update Profile Success.");
        return REDIRECT_PROFILE;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Returns a sorted list of all available ZoneId identifiers.
     * Mirrors NodeAdmin's {@code timezones} model attribute.
     */
    private List<String> getTimezones() {
        List<String> zones = new ArrayList<>(ZoneId.getAvailableZoneIds());
        Collections.sort(zones);
        return zones;
    }
}

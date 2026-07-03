package com.nodeadmin.modules.profile.controller.api.v1;

import com.nodeadmin.common.response.ResponseHandler;
import com.nodeadmin.common.route.RouteRegistry;
import com.nodeadmin.modules.access.user.entity.UserEntity;
import com.nodeadmin.modules.profile.dto.ProfileRequest;
import com.nodeadmin.modules.profile.service.IProfileService;
import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileApiController {

    private final IProfileService profileService;
    private final RouteRegistry   routeRegistry;

    public ProfileApiController(IProfileService profileService, RouteRegistry routeRegistry) {
        this.profileService = profileService;
        this.routeRegistry  = routeRegistry;
    }

    @PostConstruct
    public void registerRoutes() {
        routeRegistry.register("api.v1.profile.index",  "GET", "/api/v1/profile");
        routeRegistry.register("api.v1.profile.update", "PUT", "/api/v1/profile/update");
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> index(Authentication auth) {
        String userId = auth.getName();
        UserEntity user = profileService.getProfile(userId);
        Map<String, Object> data = Map.of(
            "id",       user.getId(),
            "name",     user.getName(),
            "email",    user.getEmail(),
            "timezone", user.getTimezone() != null ? user.getTimezone() : "",
            "picture",  user.getPicture()  != null ? user.getPicture()  : "",
            "status",   user.getStatus()   != null ? user.getStatus()   : ""
        );
        return ResponseHandler.success("OK", data);
    }

    @PutMapping("/update")
    public ResponseEntity<Map<String, Object>> update(
            @RequestBody ProfileRequest request,
            Authentication auth) {
        String userId = auth.getName();
        profileService.updateProfile(userId, request, null);
        return ResponseHandler.success("Profile updated successfully");
    }
}

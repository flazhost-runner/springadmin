package com.nodeadmin.modules.setting.controller.api.v1;

import com.nodeadmin.common.response.ResponseHandler;
import com.nodeadmin.common.route.RouteRegistry;
import com.nodeadmin.modules.setting.dto.SettingRequest;
import com.nodeadmin.modules.setting.entity.SettingEntity;
import com.nodeadmin.modules.setting.service.ISettingService;
import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/setting")
public class SettingApiController {

    private final ISettingService settingService;
    private final RouteRegistry   routeRegistry;

    public SettingApiController(ISettingService settingService, RouteRegistry routeRegistry) {
        this.settingService = settingService;
        this.routeRegistry  = routeRegistry;
    }

    @PostConstruct
    public void registerRoutes() {
        routeRegistry.register("api.v1.setting.index",  "GET", "/api/v1/setting");
        routeRegistry.register("api.v1.setting.update", "PUT", "/api/v1/setting/update");
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> index() {
        SettingEntity s = settingService.getOrCreate();
        Map<String, Object> data = Map.of(
            "id",          s.getId(),
            "name",        s.getName()       != null ? s.getName()       : "",
            "theme",       s.getTheme()      != null ? s.getTheme()      : "",
            "fe_template", s.getFeTemplate() != null ? s.getFeTemplate() : ""
        );
        return ResponseHandler.success("OK", data);
    }

    @PutMapping("/update")
    public ResponseEntity<Map<String, Object>> update(
            @RequestBody SettingRequest request,
            Authentication auth) {
        String actorId = auth != null ? auth.getName() : "system";
        settingService.update(request, null, null, null, actorId);
        return ResponseHandler.success("Setting updated successfully");
    }
}

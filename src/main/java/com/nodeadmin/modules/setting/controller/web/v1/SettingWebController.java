package com.nodeadmin.modules.setting.controller.web.v1;

import com.nodeadmin.common.model.SessionUser;
import com.nodeadmin.common.route.RouteRegistry;
import com.nodeadmin.common.util.FlashHelper;
import com.nodeadmin.config.ThemeConfig;
import com.nodeadmin.modules.home.service.IFeCatalogService;
import com.nodeadmin.modules.home.service.IFeCatalogService.CatalogEntry;
import com.nodeadmin.modules.setting.dto.SettingRequest;
import com.nodeadmin.modules.setting.entity.SettingEntity;
import com.nodeadmin.modules.setting.service.ISettingService;
import com.nodeadmin.common.util.PaginateResult;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Web (session-based) controller for the Setting resource.
 *
 * <p>Named routes:
 * <ul>
 *   <li>{@code admin.v1.setting.index}     — GET    /admin/v1/setting</li>
 *   <li>{@code admin.v1.setting.update}    — PUT    /admin/v1/setting/update</li>
 *   <li>{@code admin.v1.setting.fe_preview}— GET    /admin/v1/setting/fe-preview/{slug}</li>
 * </ul>
 *
 * <p>The index view receives:
 * <ul>
 *   <li>{@code setting}       — current {@link SettingEntity}</li>
 *   <li>{@code themes}        — list of {@link ThemeConfig.Theme} objects</li>
 *   <li>{@code currentTheme}  — active theme name string</li>
 *   <li>{@code feCatalog}     — current page of {@link CatalogEntry} items</li>
 *   <li>{@code feParams}      — pagination + filter metadata map</li>
 * </ul>
 */
@Controller
@RequestMapping("/admin/v1/setting")
public class SettingWebController {

    private static final Logger log = LoggerFactory.getLogger(SettingWebController.class);
    private final ISettingService   settingService;
    private final IFeCatalogService feCatalogService;
    private final RouteRegistry     routeRegistry;
    private final int               defaultPageSize;

    public SettingWebController(ISettingService settingService,
                                IFeCatalogService feCatalogService,
                                RouteRegistry routeRegistry,
                                com.nodeadmin.config.AppProperties appProperties) {
        this.settingService   = settingService;
        this.feCatalogService = feCatalogService;
        this.routeRegistry    = routeRegistry;
        this.defaultPageSize  = appProperties.getDefaultPageSize();
    }

    @PostConstruct
    public void registerRoutes() {
        routeRegistry.register("admin.v1.setting.index",
                "GET",    "/admin/v1/setting");
        routeRegistry.register("admin.v1.setting.update",
                "PUT",    "/admin/v1/setting/update");
        routeRegistry.register("admin.v1.setting.fe_preview",
                "GET",    "/admin/v1/setting/fe-preview/{slug}");
    }

    // =========================================================================
    // Index — GET /admin/v1/setting
    // =========================================================================

    @GetMapping
    public String index(@RequestParam(name = "q_name",     defaultValue = "") String qName,
                        @RequestParam(name = "q_category", defaultValue = "") String qCategory,
                        @RequestParam(name = "q_page",     defaultValue = "1") int qPage,
                        @RequestParam(name = "q_page_size",defaultValue = "10") int qPageSize,
                        Model model) {

        SettingEntity setting = settingService.getCachedSetting();

        // Themes
        List<ThemeConfig.Theme> themes = ThemeConfig.THEMES;
        String currentTheme = setting.getTheme() != null ? setting.getTheme() : "Blue";

        // FE Catalog
        String pinSlug = setting.getFeTemplate();
        PaginateResult<CatalogEntry> pageResult =
                feCatalogService.paginate(qName, qCategory, qPage, qPageSize, pinSlug);

        List<String> feCategories = feCatalogService.categories();

        // feParams map — exposed to template for pagination + filter state
        Map<String, Object> feParams = new LinkedHashMap<>();
        feParams.put("q_name",        qName);
        feParams.put("q_category",    qCategory);
        feParams.put("q_page",        qPage);
        feParams.put("q_page_size",   qPageSize);
        feParams.put("total_data",    pageResult.total());
        feParams.put("total_page",    pageResult.totalPages());
        feParams.put("current_page",  pageResult.page());
        feParams.put("page_size",     pageResult.pageSize());

        model.addAttribute("setting",      setting);
        model.addAttribute("themes",       themes);
        model.addAttribute("currentTheme", currentTheme);
        model.addAttribute("feCatalog",    pageResult.data());
        model.addAttribute("feParams",     feParams);
        model.addAttribute("feCategories", feCategories);

        return "modules/setting/index";
    }

    // =========================================================================
    // Update — PUT /admin/v1/setting/update
    // =========================================================================

    @PutMapping("/update")
    public String update(@ModelAttribute SettingRequest request,
                         @RequestParam(name = "icon",        required = false) MultipartFile icon,
                         @RequestParam(name = "logo",        required = false) MultipartFile logo,
                         @RequestParam(name = "login_image", required = false) MultipartFile loginImage,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {

        SessionUser actor   = (SessionUser) session.getAttribute(FlashHelper.SESSION_USER);
        String      updatedBy = (actor != null) ? actor.getId() : "system";

        log.info("[SETTING-UPDATE] theme={} name={} actor={}",
                request.getTheme(), request.getName(), updatedBy);

        try {
            settingService.update(request, icon, logo, loginImage, updatedBy);
            redirectAttributes.addFlashAttribute("flashKey", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "Save Setting Success.");
        } catch (Exception e) {
            log.error("Setting update failed: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("flashKey", "error");
            redirectAttributes.addFlashAttribute("flashMessage",
                    "Failed to save settings: " + e.getMessage());
        }

        return "redirect:/admin/v1/setting";
    }

    // =========================================================================
    // FE Preview — GET /admin/v1/setting/fe-preview/**
    // =========================================================================

    private static final String FE_PREVIEW_PREFIX = "/admin/v1/setting/fe-preview/";

    /**
     * Serves raw HTML for a template preview.
     * Uses /** to capture multi-segment slugs like "category/name-001-desc".
     */
    @GetMapping("/fe-preview/**")
    @ResponseBody
    public ResponseEntity<String> fePreview(HttpServletRequest request) {
        String uri  = request.getRequestURI();
        int    idx  = uri.indexOf(FE_PREVIEW_PREFIX);
        String slug = idx >= 0 ? uri.substring(idx + FE_PREVIEW_PREFIX.length()) : "";
        String html = feCatalogService.previewHtml(slug);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}

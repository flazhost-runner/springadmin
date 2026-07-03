package com.nodeadmin.modules.home.controller.web.v1;

import com.nodeadmin.config.ThemeConfig;
import com.nodeadmin.modules.home.service.IFeTemplateService;
import com.nodeadmin.modules.setting.entity.SettingEntity;
import com.nodeadmin.modules.setting.service.ISettingService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Public-facing home / landing page controller.
 *
 * <p>Routes (both PUBLIC — no authentication required):
 * <ul>
 *   <li>{@code GET /}     — name: web.home.root</li>
 *   <li>{@code GET /home} — name: web.home.index</li>
 * </ul>
 *
 * <p>Behaviour:
 * <ol>
 *   <li>Load the active {@link SettingEntity} via the cached service.</li>
 *   <li>If {@code setting.feTemplate} is {@code null} or equals {@code "default"}:
 *       populate the model and return the Thymeleaf view
 *       {@code fe/default/index} (renders with its own layout).</li>
 *   <li>Otherwise stream the raw HTML from {@link IFeTemplateService#getActiveHtml(String)}
 *       directly to the response — bypasses Thymeleaf entirely.</li>
 * </ol>
 */
@Controller
public class HomeController {

    private static final String DEFAULT_SLUG = "default";

    private final ISettingService    settingService;
    private final IFeTemplateService feTemplateService;

    public HomeController(ISettingService settingService,
                          IFeTemplateService feTemplateService) {
        this.settingService    = settingService;
        this.feTemplateService = feTemplateService;
    }

    // -------------------------------------------------------------------------
    // GET / — name: web.home.root
    // -------------------------------------------------------------------------

    /** Serves the public root URL. Delegates to {@link #home(Model, HttpServletResponse)}. */
    @GetMapping("/")
    public String root(Model model, HttpServletResponse response) throws IOException {
        return home(model, response);
    }

    // -------------------------------------------------------------------------
    // GET /home — name: web.home.index
    // -------------------------------------------------------------------------

    /** Serves the public /home URL. */
    @GetMapping("/home")
    public String home(Model model, HttpServletResponse response) throws IOException {
        SettingEntity setting = settingService.getCachedSetting();

        String feTemplate = setting.getFeTemplate();
        boolean useDefault = feTemplate == null
                || feTemplate.isBlank()
                || DEFAULT_SLUG.equalsIgnoreCase(feTemplate);

        if (useDefault) {
            // Populate model for the Thymeleaf fe/default/index template
            ThemeConfig.Theme theme = ThemeConfig.getByName(
                    setting.getTheme() != null ? setting.getTheme() : "Blue");
            model.addAttribute("setting", setting);
            model.addAttribute("theme", theme);
            return "fe/default/index";
        }

        // Stream the raw HTML from the cached fe-template file
        String html = feTemplateService.getActiveHtml(feTemplate);
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(html);
        response.getWriter().flush();
        // Return null tells Spring MVC we already wrote the response
        return null;
    }
}

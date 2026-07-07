package com.nodeadmin.config;

import com.nodeadmin.common.model.SessionUser;
import com.nodeadmin.common.util.FlashHelper;
import com.nodeadmin.config.security.AccessInterceptor;
import com.nodeadmin.modules.setting.entity.SettingEntity;
import com.nodeadmin.modules.setting.repository.SettingRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * MVC configuration: static resources, RBAC interceptor, and Thymeleaf model
 * enrichment for admin views.
 *
 * <h3>Resource handlers</h3>
 * <ul>
 *   <li>{@code /public/storage/**} → filesystem {@code {app.storage.root}/} (uploaded files)</li>
 *   <li>{@code /public/**}         → {@code classpath:/static/public/}</li>
 *   <li>{@code /vendor/**}         → {@code classpath:/static/vendor/}</li>
 *   <li>{@code /webjars/**}        → {@code classpath:/META-INF/resources/webjars/}</li>
 * </ul>
 *
 * <p>The {@code /public/storage/**} handler is registered before {@code /public/**}
 * so uploaded files (setting icons, media editor images) are served from the
 * filesystem storage root without being shadowed by the classpath handler.
 *
 * <h3>Interceptors</h3>
 * <ol>
 *   <li>{@link AccessInterceptor} — RBAC guard for all routes except public/auth.</li>
 *   <li>Anonymous inner {@link ThymeleafModelAttributeInterceptor} — enriches every
 *       admin {@link ModelAndView} with {@code setting}, {@code themes},
 *       {@code theme}, and {@code currentUser} so Thymeleaf layouts do not need
 *       to fetch these themselves.</li>
 * </ol>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AccessInterceptor  accessInterceptor;
    private final SettingRepository  settingRepository;
    private final AppProperties      appProperties;

    public WebMvcConfig(AccessInterceptor accessInterceptor,
                        SettingRepository settingRepository,
                        AppProperties appProperties) {
        this.accessInterceptor = accessInterceptor;
        this.settingRepository  = settingRepository;
        this.appProperties      = appProperties;
    }

    // -------------------------------------------------------------------------
    // Static resources
    // -------------------------------------------------------------------------

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Serve uploaded files (media, setting icons/logo) from the filesystem storage
        // root — but ONLY for the local driver. For oss/s3, objects live in the cloud
        // and are rendered as absolute URLs by StorageUrlBuilder, so no local serving
        // is registered. Driver alone decides — no code/view edits needed to switch.
        // Registered BEFORE /public/** so it takes precedence for /public/storage/* paths.
        // Path resolved absolutely — never CWD-relative (Porting Guide — Lesson 9).
        String driver = appProperties.getStorage().getDriver();
        if (driver == null || driver.isBlank() || "local".equalsIgnoreCase(driver)) {
            String storageRoot = Paths.get(appProperties.getStorage().getRoot())
                                       .toAbsolutePath().toString();
            registry.addResourceHandler("/public/storage/**")
                    .addResourceLocations("file:" + storageRoot + "/");
        }

        registry.addResourceHandler("/public/**")
                .addResourceLocations("classpath:/static/public/");

        registry.addResourceHandler("/vendor/**")
                .addResourceLocations("classpath:/static/vendor/");

        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    // -------------------------------------------------------------------------
    // Interceptors
    // -------------------------------------------------------------------------

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {

        // RBAC guard — applies to admin routes, skip public/auth/static
        registry.addInterceptor(accessInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns(
                        "/auth/**",
                        "/api/**",
                        "/public/**",
                        "/vendor/**",
                        "/webjars/**",
                        "/",
                        "/home"
                );

        // Thymeleaf model enricher — runs after RBAC guard so only authed views need it
        registry.addInterceptor(new ThymeleafModelAttributeInterceptor(
                settingRepository))
                .addPathPatterns("/admin/**");
    }

    // =========================================================================
    // Inner interceptor: ThymeleafModelAttributeInterceptor
    // =========================================================================

    /**
     * Adds common model attributes to every admin view so Thymeleaf layouts can
     * render the theme, site settings, and current user without explicit
     * controller boilerplate.
     *
     * <p>Attributes added:
     * <ul>
     *   <li>{@code setting}     — {@link SettingEntity} (or {@code null} if not seeded)</li>
     *   <li>{@code themes}      — {@code List<ThemeConfig.Theme>} (all 9 built-in themes)</li>
     *   <li>{@code theme}       — active {@link ThemeConfig.Theme} for the current user</li>
     *   <li>{@code currentUser} — {@link SessionUser} from the session (may be {@code null})</li>
     * </ul>
     */
    static class ThymeleafModelAttributeInterceptor implements HandlerInterceptor {

        private final SettingRepository settingRepository;

        ThymeleafModelAttributeInterceptor(SettingRepository settingRepository) {
            this.settingRepository = settingRepository;
        }

        @Override
        public void postHandle(@NonNull HttpServletRequest request,
                               @NonNull HttpServletResponse response,
                               @NonNull Object handler,
                               ModelAndView modelAndView) {

            if (modelAndView == null || modelAndView.getViewName() == null) {
                return;
            }
            // Skip redirect responses
            String viewName = modelAndView.getViewName();
            if (viewName.startsWith("redirect:") || viewName.startsWith("forward:")) {
                return;
            }

            // --- setting ---
            Optional<SettingEntity> settingOpt = settingRepository.findFirst();
            SettingEntity setting = settingOpt.orElse(null);
            modelAndView.addObject("setting", setting);

            // --- themes list ---
            List<ThemeConfig.Theme> themes = ThemeConfig.THEMES;
            modelAndView.addObject("themes", themes);

            // --- active theme ---
            String themeName = (setting != null && setting.getTheme() != null)
                    ? setting.getTheme() : "Blue";
            ThemeConfig.Theme theme = ThemeConfig.getByName(themeName);
            modelAndView.addObject("theme", theme);

            // currentTheme (String) used by setting/index.html for radio th:checked.
            // Must be sourced from the same fresh setting the interceptor just loaded,
            // not from the service cache set earlier in the controller — the two can
            // diverge during the brief window after cache.set(null) but before the next
            // getCachedSetting() re-warms the cache.
            modelAndView.addObject("currentTheme", themeName);

            // --- currentUser from session ---
            HttpSession session = request.getSession(false);
            SessionUser currentUser = (session != null)
                    ? (SessionUser) session.getAttribute(FlashHelper.SESSION_USER)
                    : null;
            modelAndView.addObject("currentUser", currentUser);

            // --- currentUri — used in sidebar active-link detection (replaces #request) ---
            modelAndView.addObject("currentUri", request.getRequestURI());
        }
    }
}

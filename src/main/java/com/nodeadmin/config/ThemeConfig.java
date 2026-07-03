package com.nodeadmin.config;

import java.util.List;
import java.util.Optional;

/**
 * Centralized theme registry — mirrors NodeAdmin's 9 built-in themes exactly.
 * Each theme carries primary / secondary / light / dark colour tokens used
 * by Thymeleaf layouts to inject CSS custom properties.
 *
 * Usage (in a controller or view helper):
 *   Theme t = ThemeConfig.getByName(userThemePref);
 */
public final class ThemeConfig {

    private ThemeConfig() {}

    // -------------------------------------------------------------------------
    // Theme record
    // -------------------------------------------------------------------------

    /**
     * Immutable colour theme descriptor.
     *
     * @param name      theme identifier (lowercase, matches app.theme setting)
     * @param primary   main brand colour (e.g. buttons, active nav)
     * @param secondary supporting colour (hover states, badges)
     * @param light     very-light tint (backgrounds, alerts)
     * @param dark      deep shade (text on light bg, dark nav)
     */
    public record Theme(String name, String primary, String secondary, String light, String dark) {}

    // -------------------------------------------------------------------------
    // Registry — order MUST match NodeAdmin exactly
    // -------------------------------------------------------------------------

    public static final List<Theme> THEMES = List.of(
            new Theme("Blue",   "#3B82F6", "#60A5FA", "#EFF6FF", "#1E40AF"),
            new Theme("Purple", "#8B5CF6", "#A78BFA", "#F5F3FF", "#5B21B6"),
            new Theme("Green",  "#10B981", "#34D399", "#ECFDF5", "#065F46"),
            new Theme("Orange", "#F59E0B", "#FCD34D", "#FFFBEB", "#92400E"),
            new Theme("Red",    "#EF4444", "#F87171", "#FEF2F2", "#991B1B")
    );

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the theme matching the given name (case-insensitive).
     * Falls back to Blue (index 0) when the name is not found.
     *
     * @param name theme name as stored in user preferences
     * @return matching Theme, never null
     */
    public static Theme getByName(String name) {
        if (name == null || name.isBlank()) {
            return THEMES.get(0);
        }
        return THEMES.stream()
                .filter(t -> t.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElse(THEMES.get(0));
    }
}

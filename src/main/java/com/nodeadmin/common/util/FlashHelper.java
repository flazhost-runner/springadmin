package com.nodeadmin.common.util;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * Utility for writing typed flash messages to a {@link RedirectAttributes}
 * instance after a POST → redirect.
 *
 * <p>Mirrors NodeAdmin's {@code req.flash()} convention:
 * <ul>
 *   <li>{@code flash_success} — single success string shown in the view.</li>
 *   <li>{@code flash_error}   — single error string shown in the view.</li>
 *   <li>{@code flash_errors}  — map of field → message for inline validation.</li>
 *   <li>{@code flash_old}     — map of field → submitted value for form repopulation.</li>
 * </ul>
 *
 * <p>All methods are static; this class is never instantiated.
 */
public final class FlashHelper {

    /** Session attribute key: current user object — matches NodeAdmin's cross-language contract. */
    public static final String SESSION_USER = "currentUser";

    private FlashHelper() {}

    // -------------------------------------------------------------------------
    // Flash keys (match Thymeleaf layout checks)
    // -------------------------------------------------------------------------

    public static final String KEY_SUCCESS = "flash_success";
    public static final String KEY_ERROR   = "flash_error";
    public static final String KEY_ERRORS  = "flash_errors";
    public static final String KEY_OLD     = "flash_old";

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Adds a success flash message.
     *
     * @param ra      Spring's {@link RedirectAttributes}
     * @param message human-readable success message
     */
    public static void success(RedirectAttributes ra, String message) {
        ra.addFlashAttribute(KEY_SUCCESS, message);
    }

    /**
     * Adds an error flash message.
     *
     * @param ra      Spring's {@link RedirectAttributes}
     * @param message human-readable error message
     */
    public static void error(RedirectAttributes ra, String message) {
        ra.addFlashAttribute(KEY_ERROR, message);
    }

    /**
     * Adds per-field validation errors and the submitted "old" values so the
     * view can repopulate the form.
     *
     * @param ra     Spring's {@link RedirectAttributes}
     * @param errors map of fieldName → error message
     * @param old    map of fieldName → submitted value (may be empty, never null)
     */
    public static void setFieldErrors(RedirectAttributes ra,
                                      Map<String, String> errors,
                                      Map<String, String> old) {
        ra.addFlashAttribute(KEY_ERRORS, errors);
        ra.addFlashAttribute(KEY_OLD,    old);
    }
}

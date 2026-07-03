package com.nodeadmin.common.handler;

import com.nodeadmin.common.error.AppError;
import com.nodeadmin.common.response.ResponseHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for all controllers.
 *
 * <p>Routes that start with {@code /api/} receive a JSON response envelope
 * (via {@link ResponseHandler}). All other routes (web) receive a flash
 * message and a redirect.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@link AppError}                       — application-level errors (4xx/5xx)</li>
 *   <li>{@link MethodArgumentNotValidException} — Bean Validation failures</li>
 *   <li>{@link NoResourceFoundException}        — 404 static/MVC resource not found</li>
 *   <li>{@link Exception}                       — catch-all 500</li>
 * </ul>
 *
 * <p>Web redirect target is always {@code /auth/login} for auth errors and
 * the HTTP referer (or {@code /}) for other errors — matching NodeAdmin's
 * flash-message pattern ({@code req.session.flashMessage}).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // =========================================================================
    // AppError — application-level (4xx / 5xx)
    // =========================================================================

    @ExceptionHandler(AppError.class)
    public Object handleAppError(AppError ex,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        log.debug("AppError [{}] {}: {}", ex.getStatus(), ex.getCode(), ex.getMessage());

        if (isApiRequest(request)) {
            return ResponseHandler.error(ex.getMessage(), ex.getStatus());
        }

        // Web: flash + redirect
        redirectAttributes.addFlashAttribute("flashKey", statusToFlashKey(ex.getStatus()));
        redirectAttributes.addFlashAttribute("flashMessage", ex.getMessage());
        return "redirect:" + resolveRedirectUrl(ex.getStatus(), request);
    }

    // =========================================================================
    // Bean Validation — @Valid failures
    // =========================================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException ex,
                                   HttpServletRequest request,
                                   RedirectAttributes redirectAttributes) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("field",   fe.getField());
                    m.put("message", fe.getDefaultMessage());
                    return m;
                })
                .collect(Collectors.toList());

        String firstMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed");

        if (isApiRequest(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status",  422);
            body.put("message", "Validation failed");
            body.put("errors",  fieldErrors);
            return ResponseEntity.unprocessableEntity().body(body);
        }

        // Web: flash first error + redirect back
        redirectAttributes.addFlashAttribute("flashKey", "error");
        redirectAttributes.addFlashAttribute("flashMessage", firstMessage);
        return "redirect:" + refererOr(request, "/");
    }

    // =========================================================================
    // 404 — NoResourceFoundException (Spring MVC static / handler not found)
    // =========================================================================

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNotFound(NoResourceFoundException ex,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) throws Exception {
        log.debug("404 Not Found: {}", request.getRequestURI());

        if (isApiRequest(request)) {
            return ResponseHandler.error("Resource not found", 404);
        }

        // Static resource requests (CSS, JS, images, fonts) must NOT redirect —
        // a redirect causes the browser to re-request the referer page with a
        // flash "Page not found" visible to the user.
        // Only redirect for HTML navigation requests (Accept: text/html).
        if (!isHtmlRequest(request)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        redirectAttributes.addFlashAttribute("flashKey", "error");
        redirectAttributes.addFlashAttribute("flashMessage", "Page not found");
        return "redirect:" + refererOr(request, "/admin/v1/dashboard");
    }

    // =========================================================================
    // Catch-all — 500
    // =========================================================================

    @ExceptionHandler(Exception.class)
    public Object handleGeneric(Exception ex,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        if (isApiRequest(request)) {
            return ResponseHandler.error("Internal server error", 500);
        }

        redirectAttributes.addFlashAttribute("flashKey", "error");
        redirectAttributes.addFlashAttribute("flashMessage", "An unexpected error occurred");
        return "redirect:" + refererOr(request, "/");
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Returns {@code true} when the response should be data (not a web redirect).
     * True for /api/** routes and for endpoints that serve raw HTML/data bodies
     * (e.g. /admin/v1/setting/fe-preview/**) which must NOT redirect on error.
     */
    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/")) return true;
        // Preview endpoints return raw HTML — treat as data response, not web navigation
        if (uri.contains("/fe-preview/")) return true;
        return false;
    }

    /**
     * Returns {@code true} when the request is a browser HTML navigation (Accept
     * header contains "text/html"). Static resource requests (CSS, JS, images,
     * fonts) typically send "text/css", "application/javascript", or "*\/*"
     * without "text/html", so they return {@code false}.
     */
    private boolean isHtmlRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    /**
     * Maps an HTTP status code to the flash key used by the Thymeleaf templates.
     * 401/403 map to {@code "error"}; everything else to {@code "error"} by default.
     */
    private String statusToFlashKey(int status) {
        return "error";
    }

    /**
     * Chooses the redirect URL for web errors.
     * 401 Unauthorized → /auth/login; all others → referer or /.
     */
    private String resolveRedirectUrl(int status, HttpServletRequest request) {
        if (status == 401) {
            return "/auth/login";
        }
        return refererOr(request, "/");
    }

    /**
     * Returns the {@code Referer} header value, or {@code fallback} when absent/blank.
     * Avoids open-redirect: only returns the path portion if the referer is an
     * absolute URL pointing to the same host.
     */
    private String refererOr(HttpServletRequest request, String fallback) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return fallback;
        }
        // Strip absolute URL to path-only to prevent open-redirect
        try {
            java.net.URI uri = new java.net.URI(referer);
            String path = uri.getPath();
            return (path != null && !path.isBlank()) ? path : fallback;
        } catch (java.net.URISyntaxException e) {
            return fallback;
        }
    }
}

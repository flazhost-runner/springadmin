package com.nodeadmin.modules.auth.controller.web.v1;

import com.nodeadmin.common.model.SessionUser;
import com.nodeadmin.common.util.FlashHelper;
import com.nodeadmin.modules.auth.dto.LoginRequest;
import com.nodeadmin.modules.auth.dto.OtpRequest;
import com.nodeadmin.modules.auth.dto.RegisterRequest;
import com.nodeadmin.modules.auth.service.IAuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Web (session-based) authentication controller.
 *
 * <p>Route names follow NodeAdmin's {@code namedRoutes} convention:
 * <ul>
 *   <li>{@code web.auth.login}          — GET  /auth/login</li>
 *   <li>{@code web.auth.login.post}     — POST /auth/login</li>
 *   <li>{@code web.auth.register}       — GET  /auth/register</li>
 *   <li>{@code web.auth.register.post}  — POST /auth/register</li>
 *   <li>{@code web.auth.logout}         — POST /auth/logout</li>
 *   <li>{@code web.auth.reset.req}      — GET  /auth/reset/req</li>
 *   <li>{@code web.auth.reset.request}  — POST /auth/reset/request</li>
 *   <li>{@code web.auth.reset.proc}     — GET  /auth/reset/proc</li>
 *   <li>{@code web.auth.reset.process}  — POST /auth/reset/process</li>
 * </ul>
 *
 * <p>Flash messages are stored as {@code RedirectAttributes} flash attributes
 * under keys {@code flashKey} (success|error) and {@code flashMessage}.
 * On login success the {@link SessionUser} is stored in the HTTP session under
 * the key {@code "currentUser"} to match the cross-language session contract.
 */
@Controller
@RequestMapping
public class AuthWebController {

    private final IAuthService authService;

    public AuthWebController(IAuthService authService) {
        this.authService = authService;
    }

    // =========================================================================
    // Login
    // =========================================================================

    /** GET /auth/login — name: web.auth.login */
    @GetMapping("/auth/login")
    public String getLogin(HttpSession session, Model model) {
        if (session.getAttribute(FlashHelper.SESSION_USER) != null) {
            return "redirect:/admin/v1/dashboard";
        }
        model.addAttribute("loginRequest", new LoginRequest());
        return "auth/login";
    }

    /** POST /auth/login — name: web.auth.login.post */
    @PostMapping("/auth/login")
    public String postLogin(@Valid LoginRequest loginRequest,
                            BindingResult bindingResult,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(FlashHelper.KEY_ERROR,
                    bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/auth/login";
        }

        SessionUser sessionUser = authService.loginWeb(
                loginRequest.getEmail(),
                loginRequest.getPassword(),
                loginRequest.isRememberMe()
        );
        session.setAttribute(FlashHelper.SESSION_USER, sessionUser);
        return "redirect:/admin/v1/dashboard";
    }

    // =========================================================================
    // Register
    // =========================================================================

    /** GET /auth/register — name: web.auth.register */
    @GetMapping("/auth/register")
    public String getRegister(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    /** POST /auth/register — name: web.auth.register.post */
    @PostMapping("/auth/register")
    public String postRegister(@Valid RegisterRequest registerRequest,
                               BindingResult bindingResult,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(FlashHelper.KEY_ERROR,
                    bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/auth/register";
        }

        authService.register(registerRequest);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Register Success.");
        return "redirect:/auth/login";
    }

    // =========================================================================
    // Logout
    // =========================================================================

    /** POST /auth/logout — name: web.auth.logout */
    @PostMapping("/auth/logout")
    public String logout(HttpSession session) {
        SecurityContextHolder.clearContext();
        session.invalidate();
        return "redirect:/auth/login";
    }

    // =========================================================================
    // Password reset — request OTP
    // =========================================================================

    /** GET /auth/reset/req — name: web.auth.reset.req */
    @GetMapping("/auth/reset/req")
    public String getResetRequest() {
        return "auth/reset_req";
    }

    /** POST /auth/reset/request — name: web.auth.reset.request */
    @PostMapping("/auth/reset/request")
    public String postResetRequest(String email,
                                   RedirectAttributes redirectAttributes) {
        authService.requestOtp(email);
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "OTP Send Success.");
        return "redirect:/auth/reset/proc";
    }

    // =========================================================================
    // Password reset — process OTP
    // =========================================================================

    /** GET /auth/reset/proc — name: web.auth.reset.proc */
    @GetMapping("/auth/reset/proc")
    public String getResetProcess(Model model) {
        model.addAttribute("otpRequest", new OtpRequest());
        return "auth/reset_proc";
    }

    /** POST /auth/reset/process — name: web.auth.reset.process */
    @PostMapping("/auth/reset/process")
    public String postResetProcess(@Valid OtpRequest otpRequest,
                                   BindingResult bindingResult,
                                   RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(FlashHelper.KEY_ERROR,
                    bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/auth/reset/proc";
        }

        authService.processOtp(
                otpRequest.getEmail(),
                otpRequest.getOtp(),
                otpRequest.getNewPassword()
        );
        redirectAttributes.addFlashAttribute(FlashHelper.KEY_SUCCESS, "Reset Password Success.");
        return "redirect:/auth/login";
    }
}

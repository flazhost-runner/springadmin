package com.nodeadmin.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for the OTP process endpoint (POST /auth/reset/process and
 * POST /api/v1/auth/reset/process).
 */
public class OtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    private String email;

    @NotBlank(message = "OTP is required")
    @Size(min = 6, max = 6, message = "OTP must be exactly 6 characters")
    private String otp;

    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "New password must be at least 8 characters")
    private String newPassword;

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getEmail()                       { return email; }
    public void   setEmail(String email)           { this.email = email; }

    public String getOtp()                         { return otp; }
    public void   setOtp(String otp)               { this.otp = otp; }

    public String getNewPassword()                 { return newPassword; }
    public void   setNewPassword(String newPassword){ this.newPassword = newPassword; }
}

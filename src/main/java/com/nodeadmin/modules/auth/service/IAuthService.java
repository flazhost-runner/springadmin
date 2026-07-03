package com.nodeadmin.modules.auth.service;

import com.nodeadmin.common.model.SessionUser;
import com.nodeadmin.modules.access.user.entity.UserEntity;
import com.nodeadmin.modules.auth.dto.RegisterRequest;

/**
 * Contract for authentication operations.
 *
 * <p>Mirrors NodeAdmin's AuthService responsibilities split across the web and
 * API controllers — credential verification, session creation, token issuance,
 * registration, and OTP-based password reset.
 */
public interface IAuthService {

    /**
     * Authenticates a user for the web (session-based) flow.
     *
     * @param email      the user's email address
     * @param password   the raw (unhashed) password
     * @param rememberMe whether to extend the session lifetime
     * @return a {@link SessionUser} ready to be stored in the HTTP session
     */
    SessionUser loginWeb(String email, String password, boolean rememberMe);

    /**
     * Authenticates a user for the API (token-based) flow.
     *
     * @param email    the user's email address
     * @param password the raw (unhashed) password
     * @return a signed JWT access token
     */
    String loginApi(String email, String password);

    /**
     * Revokes an API token by adding it to the Redis blacklist.
     *
     * @param token the Bearer token extracted from the Authorization header
     */
    void logoutApi(String token);

    /**
     * Registers a new user account.
     *
     * <p>Validates email and code uniqueness, hashes the password, and persists
     * the entity. Role assignment is intentionally absent here — public
     * self-registration must not allow self-assigning privileged roles.
     *
     * @param request the validated registration payload
     * @return the newly persisted {@link UserEntity}
     */
    UserEntity register(RegisterRequest request);

    /**
     * Initiates a password-reset flow by generating and storing a hashed OTP.
     *
     * <p>The plaintext OTP is emailed to the user (TODO: mail integration).
     * Expiry is set to 15 minutes from the time of the call.
     *
     * @param email the email address of the account to reset
     */
    void requestOtp(String email);

    /**
     * Completes a password-reset flow by verifying the OTP and updating the password.
     *
     * @param email       the account's email address
     * @param otp         the plaintext OTP from the user
     * @param newPassword the desired new password (raw, unhashed)
     */
    void processOtp(String email, String otp, String newPassword);
}

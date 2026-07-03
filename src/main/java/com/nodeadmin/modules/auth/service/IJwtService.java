package com.nodeadmin.modules.auth.service;

/**
 * Contract for JWT operations used throughout the auth module.
 *
 * <p>Mirrors NodeAdmin's JWT helpers (jwt.sign / jwt.verify / blacklist via Redis).
 * All token-lifecycle concerns live here so controllers and auth services depend
 * only on this interface, not on JJWT internals.
 */
public interface IJwtService {

    /**
     * Generates a signed JWT embedding the given claims.
     *
     * @param userId    the user's primary key (UUID string)
     * @param email     the user's email address
     * @param guardName the authentication guard — {@code "web"} or {@code "api"}
     * @return a compact, URL-safe JWT string
     */
    String generateToken(String userId, String email, String guardName);

    /**
     * Validates a JWT: checks signature, expiry, and blacklist.
     *
     * @param token the compact JWT string
     * @return {@code true} if the token is structurally valid, not expired,
     *         and not blacklisted
     */
    boolean validateToken(String token);

    /**
     * Extracts the {@code sub} claim (user ID) from a token without full validation.
     *
     * @param token the compact JWT string
     * @return the user ID embedded in the token
     */
    String extractUserId(String token);

    /**
     * Extracts the {@code guardName} claim from a token without full validation.
     *
     * @param token the compact JWT string
     * @return the guard name embedded in the token
     */
    String extractGuardName(String token);

    /**
     * Adds a token to the Redis blacklist with the given TTL.
     *
     * <p>After calling this method {@link #isBlacklisted(String)} will return
     * {@code true} until the TTL expires — matching NodeAdmin's logout flow.
     *
     * @param token        the compact JWT string to revoke
     * @param expirationMs remaining lifetime in milliseconds (used as the Redis TTL)
     */
    void blacklistToken(String token, long expirationMs);

    /**
     * Checks whether a token has been blacklisted (i.e. explicitly revoked).
     *
     * @param token the compact JWT string
     * @return {@code true} if the token is present in the Redis blacklist
     */
    boolean isBlacklisted(String token);
}

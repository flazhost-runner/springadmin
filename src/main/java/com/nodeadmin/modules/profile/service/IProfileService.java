package com.nodeadmin.modules.profile.service;

import com.nodeadmin.modules.access.user.entity.UserEntity;
import com.nodeadmin.modules.profile.dto.ProfileRequest;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service contract for the Profile module.
 *
 * <p>Mirrors NodeAdmin's profile service pattern — operates on the currently
 * authenticated user by id. Unlike {@code IUserService}, profile operations
 * intentionally do NOT touch the user's roles (roles are managed only in the
 * admin user-management module).
 *
 * <p>Supported update fields: code, name, phone, email, timezone, password,
 * status, picture. All other fields (roles, blocked, blockedReason, etc.) are
 * left unchanged.
 */
public interface IProfileService {

    /**
     * Returns the user entity for the given user id.
     *
     * @param userId the UUID of the logged-in user
     * @return the {@link UserEntity} (never {@code null}; throws if not found)
     */
    UserEntity getProfile(String userId);

    /**
     * Updates allowed profile fields for the given user.
     * Password is re-hashed only when a non-blank value is supplied.
     * Picture is replaced only when a non-empty file is provided.
     * Roles are NOT modified.
     *
     * @param userId  the UUID of the logged-in user
     * @param request validated profile DTO
     * @param picture optional new profile picture (may be {@code null} or empty)
     * @return the updated {@link UserEntity}
     */
    UserEntity updateProfile(String userId, ProfileRequest request, MultipartFile picture);
}

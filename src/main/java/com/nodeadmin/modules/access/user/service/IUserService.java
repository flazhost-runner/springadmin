package com.nodeadmin.modules.access.user.service;

import com.nodeadmin.common.util.PaginateResult;
import com.nodeadmin.modules.access.user.dto.UserRequest;
import com.nodeadmin.modules.access.user.entity.UserEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Service contract for the User resource in the access module.
 *
 * <p>Mirrors NodeAdmin's {@code IUserService} interface, adapted for Spring:
 * <ul>
 *   <li>{@link #index(Map)} — paginated list with optional filters</li>
 *   <li>{@link #store(UserRequest, String, MultipartFile[])} — create + hash password + upload picture</li>
 *   <li>{@link #edit(String)} — fetch single user with roles for edit form</li>
 *   <li>{@link #update(String, UserRequest, String, MultipartFile[])} — update + conditional re-hash</li>
 *   <li>{@link #delete(String)} — hard-delete one user</li>
 *   <li>{@link #deleteSelected(List)} — hard-delete multiple users by id</li>
 * </ul>
 *
 * <p>Supported filter keys (all prefixed with {@code q_} by callers):
 * {@code q_code}, {@code q_name}, {@code q_phone}, {@code q_email},
 * {@code q_status}, {@code q_role}, {@code q_page}, {@code q_page_size}.
 */
public interface IUserService {

    /**
     * Returns a paginated list of users, optionally filtered.
     * The result map also contains a {@code roles} key (all roles, for dropdowns).
     *
     * @param filters raw request params — caller passes the full query map
     *                including {@code q_*} prefix keys
     * @return pagination envelope plus a {@code roles} list
     */
    Map<String, Object> index(Map<String, String> filters);

    /**
     * Creates a new user, hashes the password, optionally saves a profile picture.
     *
     * @param request   validated DTO
     * @param createdBy id or email of the acting user (for audit)
     * @param files     optional uploaded files (picture); may be null or empty
     * @return the persisted {@link UserEntity}
     */
    UserEntity store(UserRequest request, String createdBy, MultipartFile[] files);

    /**
     * Returns the user with the given id, with roles loaded (for the edit form).
     * Also returns all available roles so the UI can render a selector.
     *
     * @param id user UUID
     * @return map with keys {@code data} ({@link UserEntity}) and {@code roles}
     */
    Map<String, Object> edit(String id);

    /**
     * Updates an existing user. Re-hashes the password only when a non-blank
     * password is present in the request.
     *
     * @param id        user UUID
     * @param request   validated DTO
     * @param updatedBy id or email of the acting user (for audit)
     * @param files     optional uploaded files (picture); may be null or empty
     * @return the updated {@link UserEntity}
     */
    UserEntity update(String id, UserRequest request, String updatedBy, MultipartFile[] files);

    /**
     * Hard-deletes the user with the given id. Also removes the stored picture
     * if one exists.
     *
     * @param id user UUID
     */
    void delete(String id);

    /**
     * Hard-deletes all users whose ids appear in the provided list.
     * Silently skips ids that do not exist.
     *
     * @param ids list of user UUIDs to remove
     */
    void deleteSelected(List<String> ids);
}

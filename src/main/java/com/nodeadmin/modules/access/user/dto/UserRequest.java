package com.nodeadmin.modules.access.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for user create / update operations.
 *
 * <p>Mirrors the body shape accepted by NodeAdmin's UserController
 * (code, name, phone, email, password, status, roles[]).
 * Password is optional on update — service skips hashing when null/blank.
 */
@Data
public class UserRequest {

    @NotBlank(message = "Code is required")
    @Size(max = 20, message = "Code must not exceed 20 characters")
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 50, message = "Name must not exceed 50 characters")
    private String name;

    @Size(max = 15, message = "Phone must not exceed 15 characters")
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    /** Nullable on update — service only hashes when non-blank. */
    private String password;

    /** Confirmation field — matched against password in service validation. */
    private String passwordConfirmation;

    @Size(max = 20, message = "Status must not exceed 20 characters")
    private String status = "Active";

    private String timezone = "UTC";

    private Boolean blocked = false;

    @Size(max = 255, message = "Blocked reason must not exceed 255 characters")
    private String blockedReason;

    /** Bound from multipart upload filename; actual file is resolved via @RequestPart. */
    private String picture;

    /** Role IDs to assign. At least one required on store. */
    private List<String> roles;
}

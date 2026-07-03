package com.nodeadmin.modules.profile.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for profile update requests.
 *
 * <p>Mirrors the fields accepted by NodeAdmin's profile controller:
 * code, name, phone, email, timezone, password, passwordConfirmation, status.
 * Roles are intentionally excluded — profile updates must not change roles.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProfileRequest {

    @Size(max = 20, message = "Code must be at most 20 characters")
    private String code;

    @Size(max = 50, message = "Name must be at most 50 characters")
    private String name;

    @Size(max = 15, message = "Phone must be at most 15 characters")
    private String phone;

    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    @Size(max = 255, message = "Timezone must be at most 255 characters")
    private String timezone;

    /** Leave blank to keep the current password. */
    private String password;

    private String passwordConfirmation;

    @Size(max = 20, message = "Status must be Active or Inactive")
    private String status;
}

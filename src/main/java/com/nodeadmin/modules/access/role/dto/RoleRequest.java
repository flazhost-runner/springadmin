package com.nodeadmin.modules.access.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for role create / update operations.
 *
 * <p>Mirrors NodeAdmin's RoleController body: name, status, desc.
 */
@Data
public class RoleRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Size(max = 20, message = "Status must not exceed 20 characters")
    private String status = "Active";

    @Size(max = 255, message = "Desc must not exceed 255 characters")
    private String desc;

    /** Alias getter — Thymeleaf reads {@code ${form.description}}. */
    public String getDescription() { return this.desc; }
    /** Alias setter — Spring MVC binds form field {@code name="description"}. */
    public void setDescription(String v) { this.desc = v; }
}

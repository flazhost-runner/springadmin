package com.nodeadmin.modules.access.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for permission create / update operations.
 *
 * <p>Mirrors NodeAdmin's PermissionController body: name, method, guard_name,
 * status, desc.
 */
@Data
public class PermissionRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Size(max = 10, message = "Method must not exceed 10 characters")
    private String method;

    @Size(max = 50, message = "Guard name must not exceed 50 characters")
    private String guardName;

    @Size(max = 20, message = "Status must not exceed 20 characters")
    private String status = "Active";

    @Size(max = 255, message = "Desc must not exceed 255 characters")
    private String desc;

    /** Alias getter — Thymeleaf reads {@code ${form.description}}. */
    public String getDescription() { return this.desc; }
    /** Alias setter — Spring MVC binds form field {@code name="description"}. */
    public void setDescription(String v) { this.desc = v; }
}

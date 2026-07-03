package com.nodeadmin.modules.setting.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for the Setting update form.
 *
 * <p>File fields (icon, logo, loginImage) are handled separately as
 * {@link org.springframework.web.multipart.MultipartFile} parameters in the
 * controller and are NOT part of this DTO.
 *
 * <p>Mirrors NodeAdmin's {@code SettingRequest} validator shape.
 */
@Data
@NoArgsConstructor
public class SettingRequest {

    private String initial;
    private String name;
    private String description;
    private String phone;
    private String address;
    private String email;
    private String copyright;
    private String theme;
    private String feTemplate;

    /** Accept snake_case form field name="fe_template" in addition to camelCase binding. */
    public void setFe_template(String v) { this.feTemplate = v; }
}

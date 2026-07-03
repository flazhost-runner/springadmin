package com.nodeadmin.modules.setting.service;

import com.nodeadmin.modules.setting.dto.SettingRequest;
import com.nodeadmin.modules.setting.entity.SettingEntity;
import org.springframework.web.multipart.MultipartFile;

/**
 * Contract for the Setting service.
 *
 * <p>Mirrors NodeAdmin's {@code ISettingService} interface pattern.
 * Exactly one settings row is expected in the database (singleton/get-or-create).
 */
public interface ISettingService {

    /**
     * Returns the first settings row, creating a default one if none exists yet.
     *
     * @return the current (or freshly created) settings entity
     */
    SettingEntity getOrCreate();

    /**
     * Returns the settings entity from an in-memory cache (TTL 60 s).
     * Falls back to {@link #getOrCreate()} on cache miss.
     *
     * @return cached or freshly loaded settings entity
     */
    SettingEntity getCachedSetting();

    /**
     * Applies the request payload to the existing settings row, saves
     * uploaded files to storage, sanitizes the description field, and
     * triggers {@link com.nodeadmin.modules.home.service.IFeTemplateService#ensure(String)}
     * when the {@code feTemplate} value changed.
     *
     * @param request    validated form payload
     * @param icon       optional icon upload (may be null or empty)
     * @param logo       optional logo upload (may be null or empty)
     * @param loginImage optional login-image upload (may be null or empty)
     * @param updatedBy  actor id stored in audit fields
     * @return the saved settings entity
     */
    SettingEntity update(SettingRequest request,
                         MultipartFile icon,
                         MultipartFile logo,
                         MultipartFile loginImage,
                         String updatedBy);
}

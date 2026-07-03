package com.nodeadmin.modules.setting.service;

import com.nodeadmin.common.error.AppError;
import com.nodeadmin.common.util.SanitizerUtil;
import com.nodeadmin.config.AppProperties;
import com.nodeadmin.modules.home.service.IFeTemplateService;
import com.nodeadmin.modules.setting.dto.SettingRequest;
import com.nodeadmin.modules.setting.entity.SettingEntity;
import com.nodeadmin.modules.setting.repository.SettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Concrete implementation of {@link ISettingService}.
 *
 * <p>Mirrors NodeAdmin's {@code SettingService} behaviour:
 * <ul>
 *   <li>Singleton settings row — created on first access if absent.</li>
 *   <li>In-memory cache with 60 s TTL; invalidated on every update.</li>
 *   <li>Description is HTML-sanitized via {@link SanitizerUtil#sanitizeRichText}.</li>
 *   <li>File paths resolved absolutely from {@link AppProperties#getStorage()}
 *       (never CWD-relative — Porting Guide Lesson 9).</li>
 *   <li>{@link IFeTemplateService#ensure} triggered when {@code feTemplate} changes.</li>
 * </ul>
 */
@Service
@Transactional
public class SettingService implements ISettingService {

    private static final Logger log = LoggerFactory.getLogger(SettingService.class);
    private static final long CACHE_TTL_SECONDS = 60L;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private record CacheEntry(SettingEntity entity, Instant loadedAt) {
        boolean isStale() {
            return Instant.now().isAfter(loadedAt.plusSeconds(CACHE_TTL_SECONDS));
        }
    }

    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private final SettingRepository    settingRepository;
    private final AppProperties        appProperties;
    private final IFeTemplateService   feTemplateService;

    public SettingService(SettingRepository settingRepository,
                          AppProperties appProperties,
                          IFeTemplateService feTemplateService) {
        this.settingRepository  = settingRepository;
        this.appProperties      = appProperties;
        this.feTemplateService  = feTemplateService;
    }

    // -------------------------------------------------------------------------
    // getOrCreate
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public SettingEntity getOrCreate() {
        return settingRepository.findFirst().orElseGet(() -> {
            log.info("No settings row found — creating default.");
            SettingEntity s = new SettingEntity();
            s.setTheme("Blue");
            return settingRepository.save(s);
        });
    }

    // -------------------------------------------------------------------------
    // getCachedSetting
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SettingEntity getCachedSetting() {
        CacheEntry entry = cache.get();
        if (entry != null && !entry.isStale()) {
            return entry.entity();
        }
        SettingEntity fresh = getOrCreate();
        cache.set(new CacheEntry(fresh, Instant.now()));
        return fresh;
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public SettingEntity update(SettingRequest request,
                                MultipartFile icon,
                                MultipartFile logo,
                                MultipartFile loginImage,
                                String updatedBy) {
        SettingEntity setting = getOrCreate();

        // Track whether fe_template changed (to trigger ensure after save)
        String previousFeTemplate = setting.getFeTemplate();

        // Apply plain fields
        if (request.getInitial()   != null) setting.setInitial(request.getInitial());
        if (request.getName()      != null) setting.setName(request.getName());
        if (request.getPhone()     != null) setting.setPhone(request.getPhone());
        if (request.getAddress()   != null) setting.setAddress(request.getAddress());
        if (request.getEmail()     != null) setting.setEmail(request.getEmail());
        if (request.getCopyright() != null) setting.setCopyright(request.getCopyright());
        if (request.getTheme()     != null) setting.setTheme(request.getTheme());

        // Sanitize rich-text description
        if (request.getDescription() != null) {
            setting.setDescription(SanitizerUtil.sanitizeRichText(request.getDescription()));
        }

        // fe_template
        if (request.getFeTemplate() != null && !request.getFeTemplate().isBlank()) {
            setting.setFeTemplate(request.getFeTemplate());
        }

        // File uploads
        if (icon != null && !icon.isEmpty()) {
            String path = saveFile(setting.getId(), "icon", icon);
            if (path != null) setting.setIcon(path);
        }
        if (logo != null && !logo.isEmpty()) {
            String path = saveFile(setting.getId(), "logo", logo);
            if (path != null) setting.setLogo(path);
        }
        if (loginImage != null && !loginImage.isEmpty()) {
            String path = saveFile(setting.getId(), "login_image", loginImage);
            if (path != null) setting.setLoginImage(path);
        }

        SettingEntity saved = settingRepository.save(setting);

        // Invalidate cache immediately after update
        cache.set(null);

        // Trigger FeTemplateService.ensure() if fe_template changed
        String newFeTemplate = saved.getFeTemplate();
        if (newFeTemplate != null && !newFeTemplate.isBlank()
                && !newFeTemplate.equals(previousFeTemplate)) {
            try {
                feTemplateService.ensure(newFeTemplate);
            } catch (Exception e) {
                // Non-fatal: log and continue — template will be downloaded on demand
                log.warn("FeTemplateService.ensure failed for slug '{}': {}",
                        newFeTemplate, e.getMessage());
            }
        }

        return saved;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Saves an uploaded file to {@code {storage.root}/setting/{key}.{ext}}.
     * Uses absolute path from {@link AppProperties#getStorage()#getRoot()}.
     *
     * @param settingId entity id (used for uniqueness)
     * @param key       field key: icon | logo | login_image
     * @param file      uploaded file
     * @return relative path stored in the DB, or null on failure
     */
    private String saveFile(String settingId, String key, MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "jpg";
        String relativePath = "setting/" + key + "." + ext;
        String storageRoot  = appProperties.getStorage().getRoot();
        Path   target       = Paths.get(storageRoot).toAbsolutePath()
                                   .resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            log.debug("Saved setting file {} → {}", key, target);
            return relativePath;
        } catch (IOException e) {
            log.warn("Failed to save setting file '{}': {}", key, e.getMessage());
            return null;
        }
    }
}

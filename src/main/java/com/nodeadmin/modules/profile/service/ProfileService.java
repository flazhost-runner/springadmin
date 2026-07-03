package com.nodeadmin.modules.profile.service;

import com.nodeadmin.common.error.AppError;
import com.nodeadmin.common.error.NotFoundError;
import com.nodeadmin.config.AppProperties;
import com.nodeadmin.modules.access.user.entity.UserEntity;
import com.nodeadmin.modules.access.user.repository.UserRepository;
import com.nodeadmin.modules.profile.dto.ProfileRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Concrete implementation of {@link IProfileService}.
 *
 * <p>Business rules:
 * <ul>
 *   <li>Only profile-safe fields are written: code, name, phone, email,
 *       timezone, status, password, picture. Roles are never touched.</li>
 *   <li>Password is re-hashed with BCrypt only when a non-blank value is
 *       submitted. When blank, the existing hash is preserved.</li>
 *   <li>If {@code password} and {@code passwordConfirmation} are both supplied
 *       but do not match, an {@link AppError} (422) is thrown.</li>
 *   <li>Picture is replaced on the filesystem when a non-empty file is
 *       uploaded; the DB column is updated to the new relative path.</li>
 * </ul>
 */
@Service
@Transactional
public class ProfileService implements IProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository        userRepository;
    private final BCryptPasswordEncoder bcrypt;
    private final AppProperties         appProperties;

    public ProfileService(UserRepository userRepository,
                          AppProperties appProperties) {
        this.userRepository = userRepository;
        this.appProperties  = appProperties;
        this.bcrypt         = new BCryptPasswordEncoder(appProperties.getBcrypt().getRounds());
    }

    // =========================================================================
    // getProfile
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public UserEntity getProfile(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundError("User not found"));
    }

    // =========================================================================
    // updateProfile
    // =========================================================================

    @Override
    public UserEntity updateProfile(String userId, ProfileRequest request, MultipartFile picture) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundError("User not found"));

        // -- plain fields --
        if (request.getCode()     != null && !request.getCode().isBlank())
            user.setCode(request.getCode());
        if (request.getName()     != null && !request.getName().isBlank())
            user.setName(request.getName());
        if (request.getPhone()    != null)
            user.setPhone(request.getPhone());
        if (request.getEmail()    != null && !request.getEmail().isBlank())
            user.setEmail(request.getEmail());
        if (request.getTimezone() != null && !request.getTimezone().isBlank())
            user.setTimezone(request.getTimezone());
        if (request.getStatus()   != null && !request.getStatus().isBlank())
            user.setStatus(request.getStatus());

        // -- password (optional) --
        String newPassword = request.getPassword();
        if (newPassword != null && !newPassword.isBlank()) {
            String confirm = request.getPasswordConfirmation();
            if (confirm == null || !newPassword.equals(confirm)) {
                throw new AppError(422, "VALIDATION", "Password and confirmation do not match");
            }
            user.setPassword(bcrypt.encode(newPassword));
        }

        // -- picture (optional) --
        if (picture != null && !picture.isEmpty()) {
            String picturePath = savePicture(userId, picture);
            if (picturePath != null) {
                user.setPicture(picturePath);
            }
        }

        return userRepository.save(user);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Saves the uploaded picture to {@code {storage.root}/user/{userId}.{ext}}.
     *
     * @return the relative path stored in the DB, or {@code null} on failure
     */
    private String savePicture(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "jpg";
        String relativePath = "user/" + userId + "." + ext;
        Path target = Paths.get(appProperties.getStorage().getRoot(), relativePath);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return relativePath;
        } catch (IOException e) {
            log.warn("Failed to save profile picture for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }
}

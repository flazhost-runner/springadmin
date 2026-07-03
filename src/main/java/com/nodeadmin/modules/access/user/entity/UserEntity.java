package com.nodeadmin.modules.access.user.entity;

import com.nodeadmin.common.entity.BaseEntity;
import com.nodeadmin.modules.access.role.entity.RoleEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity for the {@code users} table.
 *
 * <p>Maps to the canonical schema defined in the NodeAdmin PORTING_GUIDE (Skema DB KANONIK).
 * The primary key is a client-generated UUID string, assigned in a {@code @PrePersist}
 * hook so that the value is available before any flush (consistent with NodeAdmin's
 * TypeORM {@code @BeforeInsert} pattern).
 *
 * <p>Roles are loaded lazily via a join-table {@code users_roles}. The owning side lives
 * here; the inverse (roles→permissions) lives on {@link RoleEntity}.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity extends BaseEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "code", length = 20, unique = true, nullable = false)
    private String code;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "email", length = 255, unique = true, nullable = false)
    private String email;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "password_otp")
    private String passwordOtp;

    @Column(name = "password_otp_expires")
    private Long passwordOtpExpires;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "Active";

    @Column(name = "picture", length = 255)
    private String picture;

    @Column(name = "blocked", nullable = false)
    private Boolean blocked = false;

    @Column(name = "blocked_reason")
    private String blockedReason;

    @Column(name = "timezone", length = 255, nullable = false)
    private String timezone = "UTC";

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "users_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<RoleEntity> roles = new HashSet<>();

    /** Alias for {@link #getPicture()} — used by Thymeleaf templates via {@code ${data.pictureUrl}}. */
    public String getPictureUrl() { return this.picture; }

    /**
     * Assigns a random UUID as the primary key before the first INSERT.
     * BaseEntity.onCreate() is also called by JPA at the same lifecycle event,
     * so both hooks fire; order within the same entity is not guaranteed but
     * each sets independent fields.
     */
    @PrePersist
    protected void assignId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}

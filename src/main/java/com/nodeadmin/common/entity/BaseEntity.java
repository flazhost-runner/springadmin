package com.nodeadmin.common.entity;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * JPA mapped superclass providing audit columns for all entities.
 *
 * <p>Every concrete entity that extends {@code BaseEntity} automatically
 * inherits {@code createdAt}, {@code updatedAt}, {@code createdBy}, and
 * {@code updatedBy} columns — matching the audit columns used in NodeAdmin's
 * TypeORM entities (src/entities/BaseEntity.ts).
 *
 * <p>Usage:
 * <pre>
 * {@literal @}Entity
 * public class User extends BaseEntity { ... }
 * </pre>
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * Sets {@code createdAt} and {@code updatedAt} to the current time
     * immediately before the first INSERT.
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Refreshes {@code updatedAt} to the current time before every UPDATE.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

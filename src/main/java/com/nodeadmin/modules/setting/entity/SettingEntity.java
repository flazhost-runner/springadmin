package com.nodeadmin.modules.setting.entity;

import com.nodeadmin.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * JPA entity for the {@code settings} table.
 *
 * <p>Exactly one row is expected at runtime (singleton pattern). The
 * {@link com.nodeadmin.modules.setting.repository.SettingRepository#findFirst()}
 * default method provides a convenient accessor.
 *
 * <p>Fields map 1-to-1 with NodeAdmin's {@code settings} TypeORM entity
 * (src/modules/setting/setting.entity.ts). {@code theme} drives the
 * DB-driven colour-palette switcher; {@code feTemplate} stores the active
 * frontend landing-page template identifier.
 */
@Entity
@Table(name = "settings")
@Getter
@Setter
@NoArgsConstructor
public class SettingEntity extends BaseEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "initial", length = 255)
    private String initial;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "icon", length = 255)
    private String icon;

    @Column(name = "logo", length = 255)
    private String logo;

    @Column(name = "login_image", length = 255)
    private String loginImage;

    @Column(name = "phone", length = 255)
    private String phone;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "copyright", length = 255)
    private String copyright;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "theme", length = 20, nullable = false)
    private String theme = "Blue";

    @Column(name = "fe_template", length = 80)
    private String feTemplate;

    @PrePersist
    protected void assignId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}

package com.nodeadmin.modules.access.role.entity;

import com.nodeadmin.common.entity.BaseEntity;
import com.nodeadmin.modules.access.permission.entity.PermissionEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity for the {@code roles} table.
 *
 * <p>Roles own the join-table {@code roles_permissions} that links them to
 * {@link PermissionEntity}.
 *
 * <p>The {@code desc} column name is a reserved word in MySQL/MariaDB and is
 * escaped with backticks — Hibernate passes the identifier through verbatim
 * when dialect quoting is active, or the backtick form is understood directly
 * by the MySQL dialect.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class RoleEntity extends BaseEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "name", length = 255, unique = true, nullable = false)
    private String name;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "Active";

    @Column(name = "`desc`", length = 255)
    private String desc;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "roles_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<PermissionEntity> permissions = new HashSet<>();

    /** Alias for {@link #getDesc()} — used by Thymeleaf templates via {@code ${data.description}}. */
    public String getDescription() { return this.desc; }

    @PrePersist
    protected void assignId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}

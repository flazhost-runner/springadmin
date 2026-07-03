package com.nodeadmin.modules.access.permission.entity;

import com.nodeadmin.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * JPA entity for the {@code permissions} table.
 *
 * <p>Permissions in NodeAdmin are ROUTE-DRIVEN: each named route produces one
 * permission record whose {@code name} is the route name and whose {@code method}
 * is the HTTP verb (GET, POST, PUT, DELETE, …). The combination
 * {@code (name, method)} is unique — a GET and a DELETE on the same logical
 * resource are treated as distinct permissions.
 *
 * <p>The {@code guard_name} distinguishes session-based ({@code "web"}) from
 * token-based ({@code "api"}) routes, derived from the route-name prefix
 * ({@code "api.*" → "api"}, otherwise {@code "web"}).
 *
 * <p>The {@code desc} column is backtick-quoted because {@code DESC} is a
 * reserved keyword in MySQL/MariaDB DDL.
 */
@Entity
@Table(
        name = "permissions",
        indexes = {
                @Index(name = "idx_permissions_name", columnList = "name")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class PermissionEntity extends BaseEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "guard_name", length = 50)
    private String guardName;

    @Column(name = "method", length = 10)
    private String method;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "Active";

    @Column(name = "`desc`", length = 255)
    private String desc;

    /** Alias for {@link #getDesc()} — used by Thymeleaf templates via {@code ${data.description}}. */
    public String getDescription() { return this.desc; }

    @PrePersist
    protected void assignId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}

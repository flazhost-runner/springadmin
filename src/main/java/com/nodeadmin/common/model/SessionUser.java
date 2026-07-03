package com.nodeadmin.common.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializable session payload stored in Redis via Spring Session.
 *
 * <p>Mirrors NodeAdmin's {@code ISessionUser} interface. Intentionally a plain
 * class (not a record) because Java records are not reliably deserialised by
 * all Jackson/Redis configurations — fields must be mutable for Jackson's
 * default no-arg constructor + setter pattern.
 *
 * <p>Stored under the {@code SESSION_USER} attribute key in
 * {@code HttpSession}. Populated by the auth controller on successful login
 * and cleared on logout.
 */
public class SessionUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String code;
    private String name;
    private String email;
    private String picture;
    private String timezone;
    private String status;
    private List<String> roles = new ArrayList<>();

    /** Primary role ID — used by GlobalViewDataAdvice.HasAccessHelper for RBAC lookups. */
    private String roleId;

    /** Primary role display name — mirrors NodeAdmin's sessionUser.roleName. */
    private String roleName;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public SessionUser() {}

    public SessionUser(String id, String code, String name, String email,
                       String picture, String timezone, String status,
                       List<String> roles) {
        this.id       = id;
        this.code     = code;
        this.name     = name;
        this.email    = email;
        this.picture  = picture;
        this.timezone = timezone;
        this.status   = status;
        this.roles    = roles != null ? new ArrayList<>(roles) : new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Convenience helpers
    // -------------------------------------------------------------------------

    /** Returns {@code true} if the user carries the "Administrator" role. */
    public boolean isAdministrator() {
        return roles != null && roles.contains("Administrator");
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getId()                      { return id; }
    public void   setId(String id)             { this.id = id; }

    public String getCode()                    { return code; }
    public void   setCode(String code)         { this.code = code; }

    public String getName()                    { return name; }
    public void   setName(String name)         { this.name = name; }

    public String getEmail()                   { return email; }
    public void   setEmail(String email)       { this.email = email; }

    public String getPicture()                 { return picture; }
    public void   setPicture(String picture)   { this.picture = picture; }

    public String getTimezone()                { return timezone; }
    public void   setTimezone(String timezone) { this.timezone = timezone; }

    public String getStatus()                  { return status; }
    public void   setStatus(String status)     { this.status = status; }

    public List<String> getRoles()                       { return roles; }
    public void         setRoles(List<String> roles)     { this.roles = roles; }

    public String getRoleId()                            { return roleId; }
    public void   setRoleId(String roleId)               { this.roleId = roleId; }

    public String getRoleName()                          { return roleName; }
    public void   setRoleName(String roleName)           { this.roleName = roleName; }
}

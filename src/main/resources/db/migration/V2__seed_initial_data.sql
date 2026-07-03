-- =============================================================================
-- V2__seed_initial_data.sql
-- Seed: Administrator role, admin user, user-role assignment, default settings.
-- Flyway guarantees each migration runs exactly once, so no INSERT IGNORE needed.
-- Password hash = bcrypt(12 rounds) of "12345678" — change in production.
-- Compatible with: MySQL 8+, PostgreSQL 14+, SQLite 3.x
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Administrator role
-- ---------------------------------------------------------------------------
INSERT INTO roles
    (id, name, status, `desc`, created_by, updated_by, created_at, updated_at)
VALUES
    (
        '00000000-0000-0000-0000-000000000002',
        'Administrator',
        'Active',
        '',
        NULL,
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

-- ---------------------------------------------------------------------------
-- 2. Admin user
--    password hash = bcrypt(10, "12345678") — matches NodeAdmin default BCRYPT_ROUNDS=10
-- ---------------------------------------------------------------------------
INSERT INTO users
    (id, code, name, phone, email, email_verified_at, password,
     password_otp, password_otp_expires, status, picture,
     blocked, blocked_reason, timezone,
     created_by, updated_by, created_at, updated_at)
VALUES
    (
        '00000000-0000-0000-0000-000000000003',
        '0000000001',
        'Administrator',
        '12345678910',
        'admin@admin.com',
        CURRENT_TIMESTAMP,
        '$2a$10$s6QlWQGn5lk.vHJLgereKOnw1RrLDfpDsQvZRXEufTDhyTHSO19oa',
        NULL,
        NULL,
        'Active',
        NULL,
        FALSE,
        '',
        'Asia/Jakarta',
        NULL,
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

-- ---------------------------------------------------------------------------
-- 3. Assign Administrator role to admin user
-- ---------------------------------------------------------------------------
INSERT INTO users_roles (user_id, role_id)
VALUES (
    '00000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000002'
);

-- ---------------------------------------------------------------------------
-- 4. Default application settings
-- ---------------------------------------------------------------------------
INSERT INTO settings
    (id, initial, name, description, icon, logo, login_image,
     phone, address, email, copyright,
     theme, fe_template,
     created_by, updated_by, created_at, updated_at)
VALUES
    (
        '00000000-0000-0000-0000-000000000001',
        NULL,
        'SpringAdmin',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        'Blue',
        'agency-consulting-002-creative-agency',
        NULL,
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

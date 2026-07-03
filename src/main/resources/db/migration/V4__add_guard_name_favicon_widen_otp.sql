-- =============================================================================
-- V4__add_guard_name_favicon_widen_otp.sql
-- Align SpringAdmin schema with NodeAdmin standard:
--   1. roles.guard_name  (missing from V1)
--   2. settings.favicon  (missing from V1)
--   3. users.password_otp width — handled in V1 (VARCHAR(255) for bcrypt hashes).
-- =============================================================================

-- 1. Add guard_name to roles (compatible with MySQL, PostgreSQL, SQLite)
ALTER TABLE roles ADD COLUMN guard_name VARCHAR(20) NOT NULL DEFAULT 'web';

-- 2. Add favicon to settings (compatible with MySQL, PostgreSQL, SQLite)
ALTER TABLE settings ADD COLUMN favicon VARCHAR(255) NULL;

-- 3. Widen password_otp for bcrypt storage (bcrypt = 60 chars)
--    Resolved in V1 (portability fix): users.password_otp is now created as
--    VARCHAR(255) directly, so no ALTER is needed here on any database.

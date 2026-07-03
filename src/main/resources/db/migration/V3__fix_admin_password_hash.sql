-- =============================================================================
-- V3__fix_admin_password_hash.sql
-- Corrects the admin user's BCrypt password hash.
-- The V2 seed contained an invalid hash for "Admin1234!".
-- This migration replaces it with the correct 12-round BCrypt hash.
-- Password: Admin1234!
-- =============================================================================

UPDATE users
SET password = '$2a$12$bGK3u11Z3p8PbvNuzk3u9OzrVgxIUmnmsJFOMnOKclfFruwvl46Ve'
WHERE id = '00000000-0000-0000-0000-000000000003'
  AND email = 'admin@example.com';

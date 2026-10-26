-- ============================================================
-- V9__administration_settings.sql
-- ERP Portal — Administration & System Settings
-- REQ-ADM-001..008: Administration, Users, Roles, Audit, Settings, Import, Export
-- ============================================================

CREATE TABLE system_settings (
    setting_key     VARCHAR(100)    NOT NULL,
    setting_value   TEXT            NOT NULL,
    category        VARCHAR(50)     NOT NULL DEFAULT 'GENERAL',
    description     VARCHAR(255),
    updated_by      BIGINT,
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version         INT             NOT NULL DEFAULT 1,
    CONSTRAINT pk_system_settings PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE system_settings_history (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    setting_key     VARCHAR(100)    NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    changed_by      BIGINT,
    changed_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version         INT             NOT NULL,
    CONSTRAINT pk_system_settings_history PRIMARY KEY (id),
    KEY idx_settings_hist_key (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Initial default system settings
INSERT INTO system_settings (setting_key, setting_value, category, description, version) VALUES
    ('institution.name', 'Acme International School', 'INSTITUTION', 'Official registered name of the institution', 1),
    ('institution.code', 'ACME-2026', 'INSTITUTION', 'Unique institution code identifier', 1),
    ('institution.email', 'admin@acmeschool.edu', 'INSTITUTION', 'Primary administrative contact email', 1),
    ('institution.phone', '+1-555-0199', 'INSTITUTION', 'Primary administrative contact phone', 1),
    ('institution.address', '123 Education Blvd, Knowledge City', 'INSTITUTION', 'Physical campus address', 1),
    ('academic.active_year', '2026-2027', 'ACADEMIC', 'Current operational academic year', 1),
    ('security.session_timeout_minutes', '60', 'SECURITY', 'Idle session timeout in minutes', 1),
    ('security.max_login_attempts', '5', 'SECURITY', 'Failed attempts before account auto-lock', 1),
    ('system.notification_banner', 'Welcome to ERP Portal — Academic Year 2026-2027 Active', 'GENERAL', 'Global broadcast banner message', 1);

-- Administration & RBAC permissions
INSERT INTO permissions (code, description, module) VALUES
    ('USER:CREATE', 'Create user accounts', 'USER'),
    ('USER:READ', 'Read user accounts and profiles', 'USER'),
    ('USER:UPDATE', 'Update user profiles and details', 'USER'),
    ('USER:MANAGE', 'Activate, deactivate, lock, unlock user accounts', 'USER'),
    ('USER:IMPORT', 'Bulk import users via CSV', 'USER'),
    ('USER:EXPORT', 'Export user catalog to CSV', 'USER'),
    ('RBAC:ROLE_CREATE', 'Create custom roles', 'RBAC'),
    ('RBAC:ROLE_UPDATE', 'Update role details and descriptions', 'RBAC'),
    ('AUDIT:READ', 'Search and view system audit logs', 'AUDIT'),
    ('AUDIT:EXPORT', 'Export system audit logs to CSV', 'AUDIT'),
    ('SETTINGS:READ', 'Read global system configuration settings', 'SETTINGS'),
    ('SETTINGS:UPDATE', 'Update global system configuration settings', 'SETTINGS')
ON DUPLICATE KEY UPDATE description = VALUES(description), module = VALUES(module);

-- Grant all newly registered administrative permissions to SUPER_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'USER:CREATE','USER:READ','USER:UPDATE','USER:MANAGE','USER:IMPORT','USER:EXPORT',
    'RBAC:ROLE_CREATE','RBAC:ROLE_UPDATE',
    'AUDIT:READ','AUDIT:EXPORT',
    'SETTINGS:READ','SETTINGS:UPDATE'
)
WHERE r.code = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

-- Authentication tokens are opaque random values. Only their SHA-256 digest is stored.
CREATE TABLE auth_sessions (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    token_hash  CHAR(64)        NOT NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at  DATETIME(6)     NOT NULL,
    revoked_at  DATETIME(6),
    CONSTRAINT pk_auth_sessions PRIMARY KEY (id),
    CONSTRAINT uq_auth_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    KEY idx_auth_sessions_user_expiry (user_id, expires_at) COMMENT 'Find a user’s active and expired sessions',
    KEY idx_auth_sessions_expiry (expires_at) COMMENT 'Locate expired sessions for cleanup'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO roles (code, name, description, is_system) VALUES
    ('SUPER_ADMIN', 'Super Admin', 'Full system security administration', 1),
    ('ADMIN', 'Admin', 'Institution administrator', 1),
    ('MANAGEMENT', 'Management', 'Institution management', 1),
    ('TEACHER', 'Teacher', 'Teaching staff', 1),
    ('ACCOUNTANT', 'Accountant', 'Finance staff', 1),
    ('STAFF_ADMIN', 'HR / STAFF_ADMIN', 'Human resources and staff administration', 1),
    ('LIBRARIAN', 'Librarian', 'Library staff', 1),
    ('RECEPTION_ADMISSION', 'RECEPTION / ADMISSION', 'Reception and admission staff', 1),
    ('INVENTORY_MANAGER', 'Inventory Manager', 'Inventory staff', 1),
    ('SUPPORT_AGENT', 'Support Agent', 'Support staff', 1),
    ('PARENT_GUARDIAN', 'PARENT / GUARDIAN', 'Parent or guardian portal user', 1),
    ('STUDENT', 'Student', 'Student portal user', 1);

INSERT INTO permissions (code, description, module) VALUES
    ('RBAC:ROLE_READ', 'Read role definitions and grants', 'RBAC'),
    ('RBAC:PERMISSION_READ', 'Read permission definitions', 'RBAC'),
    ('RBAC:USER_ROLE_UPDATE', 'Change user role assignments', 'RBAC'),
    ('RBAC:ROLE_PERMISSION_UPDATE', 'Change role permission grants', 'RBAC'),
    ('STUDENT:READ', 'Read student records', 'STUDENT'),
    ('STUDENT:CREATE', 'Create student records', 'STUDENT'),
    ('STUDENT:UPDATE', 'Update student records', 'STUDENT'),
    ('ATTENDANCE:MARK', 'Record attendance', 'ATTENDANCE'),
    ('FEE:COLLECT', 'Record a fee payment', 'FEE'),
    ('FEE_DISCOUNT:APPROVE', 'Approve fee discounts', 'FEE_DISCOUNT'),
    ('REPORT:EXPORT', 'Export reports', 'REPORT');

-- The seeded roles intentionally start with no business permissions. The
-- bootstrap administrator receives the full initial catalog; all other grants
-- require an explicit, audited RBAC change.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'SUPER_ADMIN';

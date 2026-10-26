-- Preserve the class relationship captured when attendance was taken.
SET @attendance_class_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'attendance'
      AND COLUMN_NAME = 'class_id'
);
SET @sql_attendance_class = IF(@attendance_class_exists = 0,
    'ALTER TABLE attendance ADD COLUMN class_id BIGINT NULL AFTER session_id',
    'SELECT 1');
PREPARE stmt_attendance_class FROM @sql_attendance_class;
EXECUTE stmt_attendance_class;
DEALLOCATE PREPARE stmt_attendance_class;

SET @attendance_idempotency_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'attendance'
      AND COLUMN_NAME = 'idempotency_key'
);
SET @sql_attendance_idempotency = IF(@attendance_idempotency_exists = 0,
    'ALTER TABLE attendance ADD COLUMN idempotency_key VARCHAR(100) NULL AFTER remarks',
    'SELECT 1');
PREPARE stmt_attendance_idempotency FROM @sql_attendance_idempotency;
EXECUTE stmt_attendance_idempotency;
DEALLOCATE PREPARE stmt_attendance_idempotency;

SET @attendance_marker_user_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'attendance'
      AND COLUMN_NAME = 'marked_by_user_id'
);
SET @sql_attendance_marker_user = IF(@attendance_marker_user_exists = 0,
    'ALTER TABLE attendance ADD COLUMN marked_by_user_id BIGINT NULL AFTER marked_by',
    'SELECT 1');
PREPARE stmt_attendance_marker_user FROM @sql_attendance_marker_user;
EXECUTE stmt_attendance_marker_user;
DEALLOCATE PREPARE stmt_attendance_marker_user;

ALTER TABLE attendance MODIFY COLUMN marked_by BIGINT NULL;

UPDATE attendance a
JOIN sections s ON s.id = a.section_id
SET a.class_id = s.class_id
WHERE a.class_id IS NULL;

ALTER TABLE attendance MODIFY class_id BIGINT NOT NULL;

SET @fk_att_class_exists = (
    SELECT COUNT(*)
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'attendance'
      AND CONSTRAINT_NAME = 'fk_att_class'
);
SET @sql_fk_att_class = IF(@fk_att_class_exists = 0,
    'ALTER TABLE attendance ADD CONSTRAINT fk_att_class FOREIGN KEY (class_id) REFERENCES classes(id)',
    'SELECT 1');
PREPARE stmt_fk_att_class FROM @sql_fk_att_class;
EXECUTE stmt_fk_att_class;
DEALLOCATE PREPARE stmt_fk_att_class;

SET @fk_att_marker_user_exists = (
    SELECT COUNT(*)
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'attendance'
      AND CONSTRAINT_NAME = 'fk_att_marker_user'
);
SET @sql_fk_att_marker_user = IF(@fk_att_marker_user_exists = 0,
    'ALTER TABLE attendance ADD CONSTRAINT fk_att_marker_user FOREIGN KEY (marked_by_user_id) REFERENCES users(id)',
    'SELECT 1');
PREPARE stmt_fk_att_marker_user FROM @sql_fk_att_marker_user;
EXECUTE stmt_fk_att_marker_user;
DEALLOCATE PREPARE stmt_fk_att_marker_user;

SET @uq_att_idempotency_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'attendance'
      AND INDEX_NAME = 'uq_att_idempotency_key'
);
SET @sql_uq_att_idempotency = IF(@uq_att_idempotency_exists = 0,
    'CREATE UNIQUE INDEX uq_att_idempotency_key ON attendance(idempotency_key)',
    'SELECT 1');
PREPARE stmt_uq_att_idempotency FROM @sql_uq_att_idempotency;
EXECUTE stmt_uq_att_idempotency;
DEALLOCATE PREPARE stmt_uq_att_idempotency;

SET @staff_attendance_idempotency_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'staff_attendance'
      AND COLUMN_NAME = 'idempotency_key'
);
SET @sql_staff_attendance_idempotency = IF(@staff_attendance_idempotency_exists = 0,
    'ALTER TABLE staff_attendance ADD COLUMN idempotency_key VARCHAR(100) NULL AFTER remarks',
    'SELECT 1');
PREPARE stmt_staff_attendance_idempotency FROM @sql_staff_attendance_idempotency;
EXECUTE stmt_staff_attendance_idempotency;
DEALLOCATE PREPARE stmt_staff_attendance_idempotency;

SET @staff_attendance_updated_at_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'staff_attendance'
      AND COLUMN_NAME = 'updated_at'
);
SET @sql_staff_attendance_updated_at = IF(@staff_attendance_updated_at_exists = 0,
    'ALTER TABLE staff_attendance ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)',
    'SELECT 1');
PREPARE stmt_staff_attendance_updated_at FROM @sql_staff_attendance_updated_at;
EXECUTE stmt_staff_attendance_updated_at;
DEALLOCATE PREPARE stmt_staff_attendance_updated_at;

SET @uq_staff_att_idempotency_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'staff_attendance'
      AND INDEX_NAME = 'uq_staff_att_idempotency_key'
);
SET @sql_uq_staff_att_idempotency = IF(@uq_staff_att_idempotency_exists = 0,
    'CREATE UNIQUE INDEX uq_staff_att_idempotency_key ON staff_attendance(idempotency_key)',
    'SELECT 1');
PREPARE stmt_uq_staff_att_idempotency FROM @sql_uq_staff_att_idempotency;
EXECUTE stmt_uq_staff_att_idempotency;
DEALLOCATE PREPARE stmt_uq_staff_att_idempotency;

UPDATE attendance a
JOIN staff st ON st.id = a.marked_by
SET a.marked_by_user_id = st.user_id
WHERE a.marked_by_user_id IS NULL AND st.user_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS attendance_date_locks (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    lock_scope      ENUM('STUDENT','STAFF') NOT NULL,
    session_id      BIGINT,
    attendance_date DATE            NOT NULL,
    reason          VARCHAR(500)    NOT NULL,
    locked_by       BIGINT          NOT NULL,
    locked_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lock_key        VARCHAR(80)     NOT NULL,
    CONSTRAINT pk_attendance_date_locks PRIMARY KEY (id),
    CONSTRAINT uq_attendance_date_lock_key UNIQUE (lock_key),
    CONSTRAINT fk_att_lock_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id),
    CONSTRAINT fk_att_lock_user FOREIGN KEY (locked_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS staff_attendance_corrections (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    staff_attendance_id BIGINT      NOT NULL,
    old_status      VARCHAR(20)     NOT NULL,
    new_status      VARCHAR(20)     NOT NULL,
    reason          VARCHAR(500)    NOT NULL,
    corrected_by    BIGINT          NOT NULL,
    corrected_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_staff_att_corrections PRIMARY KEY (id),
    CONSTRAINT fk_sac_attendance FOREIGN KEY (staff_attendance_id) REFERENCES staff_attendance(id),
    CONSTRAINT fk_sac_user FOREIGN KEY (corrected_by) REFERENCES users(id),
    KEY idx_staff_att_correction_history (staff_attendance_id, corrected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @idx_attendance_class_date_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'attendance'
      AND INDEX_NAME = 'idx_attendance_class_date'
);
SET @sql_idx_attendance_class_date = IF(@idx_attendance_class_date_exists = 0,
    'CREATE INDEX idx_attendance_class_date ON attendance(class_id, attendance_date)',
    'SELECT 1');
PREPARE stmt_idx_attendance_class_date FROM @sql_idx_attendance_class_date;
EXECUTE stmt_idx_attendance_class_date;
DEALLOCATE PREPARE stmt_idx_attendance_class_date;

SET @idx_staff_attendance_date_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'staff_attendance'
      AND INDEX_NAME = 'idx_staff_attendance_date'
);
SET @sql_idx_staff_attendance_date = IF(@idx_staff_attendance_date_exists = 0,
    'CREATE INDEX idx_staff_attendance_date ON staff_attendance(attendance_date)',
    'SELECT 1');
PREPARE stmt_idx_staff_attendance_date FROM @sql_idx_staff_attendance_date;
EXECUTE stmt_idx_staff_attendance_date;
DEALLOCATE PREPARE stmt_idx_staff_attendance_date;

INSERT INTO permissions (code, description, module) VALUES
    ('ATTENDANCE:READ', 'Read attendance in permitted scope', 'ATTENDANCE'),
    ('ATTENDANCE:CORRECT', 'Correct student attendance with a reason', 'ATTENDANCE'),
    ('ATTENDANCE:MANAGE', 'Manage student attendance and date locks', 'ATTENDANCE'),
    ('STAFF_ATTENDANCE:READ', 'Read staff attendance in permitted scope', 'STAFF_ATTENDANCE'),
    ('STAFF_ATTENDANCE:MARK', 'Mark staff attendance', 'STAFF_ATTENDANCE'),
    ('STAFF_ATTENDANCE:CORRECT', 'Correct staff attendance with a reason', 'STAFF_ATTENDANCE'),
    ('STAFF_ATTENDANCE:MANAGE', 'Manage staff attendance and date locks', 'STAFF_ATTENDANCE')
ON DUPLICATE KEY UPDATE description = VALUES(description), module = VALUES(module);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'ATTENDANCE:READ','ATTENDANCE:MARK','ATTENDANCE:CORRECT','ATTENDANCE:MANAGE',
    'STAFF_ATTENDANCE:READ','STAFF_ATTENDANCE:MARK','STAFF_ATTENDANCE:CORRECT','STAFF_ATTENDANCE:MANAGE'
)
WHERE r.code = 'SUPER_ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );

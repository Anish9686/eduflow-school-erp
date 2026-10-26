-- ============================================================
-- V1__baseline_schema.sql
-- ERP Portal — Baseline Schema
-- REQ-ARCH-003: Flyway-controlled, reproducible migration
-- REQ-SEC-001: Passwords hashed (never plaintext)
-- REQ-AUTHZ-001..005: RBAC tables
-- REQ-STU-001..008: Student lifecycle tables
-- REQ-ACA-001..005: Academic structure tables
-- REQ-ATT-001..005: Attendance tables
-- REQ-FIN-001..009: Finance tables
-- REQ-OPS-001..005: Operations tables
-- REQ-ADM-001..008: Administration / Audit tables
-- ============================================================

-- ============================================================
-- SECTION 1: IDENTITY & RBAC
-- ============================================================

CREATE TABLE roles (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)     NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    description VARCHAR(255),
    is_system   TINYINT(1)      NOT NULL DEFAULT 1,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE permissions (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    code        VARCHAR(100)    NOT NULL COMMENT 'MODULE:ACTION format per REQ-AUTHZ-001',
    description VARCHAR(255),
    module      VARCHAR(50)     NOT NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uq_permissions_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE role_permissions (
    role_id         BIGINT NOT NULL,
    permission_id   BIGINT NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    username        VARCHAR(100)    NOT NULL,
    email           VARCHAR(254)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL COMMENT 'BCrypt adaptive hash — REQ-SEC-001',
    full_name       VARCHAR(200),
    phone           VARCHAR(30),
    status          ENUM('ACTIVE','LOCKED','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    last_login_at   DATETIME(6),
    failed_attempts INT             NOT NULL DEFAULT 0,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by      BIGINT,
    updated_by      BIGINT,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Token revocation / blacklist (REQ-AUTH-004)
CREATE TABLE revoked_tokens (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    jti         VARCHAR(100)    NOT NULL COMMENT 'JWT ID claim',
    user_id     BIGINT          NOT NULL,
    revoked_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at  DATETIME(6)     NOT NULL,
    CONSTRAINT pk_revoked_tokens PRIMARY KEY (id),
    CONSTRAINT uq_revoked_jti UNIQUE (jti),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- SECTION 2: STUDENT LIFECYCLE (REQ-STU-001..008)
-- ============================================================

CREATE TABLE guardians (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(150)    NOT NULL,
    phone       VARCHAR(30),
    email       VARCHAR(254),
    address     TEXT,
    occupation  VARCHAR(100),
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_guardians PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE students (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    admission_no    VARCHAR(50)     NOT NULL COMMENT 'Unique within institution — REQ-STU-002',
    first_name      VARCHAR(100)    NOT NULL,
    last_name       VARCHAR(100),
    date_of_birth   DATE,
    gender          ENUM('MALE','FEMALE','OTHER'),
    blood_group     VARCHAR(5),
    religion        VARCHAR(50),
    category        VARCHAR(50),
    phone           VARCHAR(30),
    email           VARCHAR(254),
    address         TEXT,
    photo_url       VARCHAR(500),
    status          ENUM('APPLIED','ACTIVE','TRANSFERRED','EXITED') NOT NULL DEFAULT 'APPLIED'
                    COMMENT 'REQ-STU-006: valid transitions only',
    admission_date  DATE,
    exit_date       DATE,
    exit_reason     VARCHAR(500),
    user_id         BIGINT          COMMENT 'Linked portal account',
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by      BIGINT,
    updated_by      BIGINT,
    CONSTRAINT pk_students PRIMARY KEY (id),
    CONSTRAINT uq_students_admission_no UNIQUE (admission_no),
    CONSTRAINT fk_stu_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_guardians (
    student_id      BIGINT          NOT NULL,
    guardian_id     BIGINT          NOT NULL,
    relation        VARCHAR(30)     NOT NULL COMMENT 'Father/Mother/Guardian etc',
    is_primary      TINYINT(1)      NOT NULL DEFAULT 0,
    CONSTRAINT pk_student_guardians PRIMARY KEY (student_id, guardian_id),
    CONSTRAINT fk_sg_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_sg_guardian FOREIGN KEY (guardian_id) REFERENCES guardians(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_documents (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    student_id      BIGINT          NOT NULL,
    doc_type        VARCHAR(60)     NOT NULL COMMENT 'BIRTH_CERT/TRANSFER_CERT/MARKSHEET/etc',
    file_name       VARCHAR(255)    NOT NULL,
    file_path       VARCHAR(1000)   NOT NULL,
    file_size_bytes BIGINT,
    mime_type       VARCHAR(100),
    uploaded_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    uploaded_by     BIGINT,
    CONSTRAINT pk_student_documents PRIMARY KEY (id),
    CONSTRAINT fk_sd_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- SECTION 3: ACADEMICS (REQ-ACA-001..005)
-- ============================================================

CREATE TABLE academic_sessions (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    code        VARCHAR(30)     NOT NULL COMMENT 'e.g. 2026-27 — unique',
    name        VARCHAR(100)    NOT NULL,
    start_date  DATE            NOT NULL,
    end_date    DATE            NOT NULL,
    is_active   TINYINT(1)      NOT NULL DEFAULT 0,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_academic_sessions PRIMARY KEY (id),
    CONSTRAINT uq_academic_sessions_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE classes (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50)     NOT NULL COMMENT 'Grade 1, Class 10, etc.',
    order_seq   INT             NOT NULL DEFAULT 0,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_classes PRIMARY KEY (id),
    CONSTRAINT uq_classes_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sections (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    class_id    BIGINT          NOT NULL,
    name        VARCHAR(10)     NOT NULL COMMENT 'A, B, C etc.',
    capacity    INT,
    room_no     VARCHAR(20),
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_sections PRIMARY KEY (id),
    CONSTRAINT uq_sections_class_name UNIQUE (class_id, name),
    CONSTRAINT fk_sec_class FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_enrollments (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    student_id      BIGINT          NOT NULL,
    session_id      BIGINT          NOT NULL,
    class_id        BIGINT          NOT NULL,
    section_id      BIGINT          NOT NULL,
    roll_no         VARCHAR(20),
    enrolled_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_student_enrollments PRIMARY KEY (id),
    CONSTRAINT uq_enrollment UNIQUE (student_id, session_id),
    CONSTRAINT fk_enr_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_enr_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id),
    CONSTRAINT fk_enr_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_enr_section FOREIGN KEY (section_id) REFERENCES sections(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE subjects (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    code        VARCHAR(30)     NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    description VARCHAR(255),
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_subjects PRIMARY KEY (id),
    CONSTRAINT uq_subjects_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE staff (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    employee_no     VARCHAR(50)     NOT NULL,
    first_name      VARCHAR(100)    NOT NULL,
    last_name       VARCHAR(100),
    email           VARCHAR(254),
    phone           VARCHAR(30),
    designation     VARCHAR(100),
    department      VARCHAR(100),
    join_date       DATE,
    status          ENUM('ACTIVE','ON_LEAVE','RESIGNED','TERMINATED') NOT NULL DEFAULT 'ACTIVE',
    user_id         BIGINT,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_staff PRIMARY KEY (id),
    CONSTRAINT uq_staff_employee_no UNIQUE (employee_no),
    CONSTRAINT fk_staff_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Section-subject-teacher assignments (REQ-ACA-002)
CREATE TABLE section_subjects (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    session_id  BIGINT      NOT NULL,
    section_id  BIGINT      NOT NULL,
    subject_id  BIGINT      NOT NULL,
    teacher_id  BIGINT      COMMENT 'Staff assigned as teacher',
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_section_subjects PRIMARY KEY (id),
    CONSTRAINT uq_sec_sub_session UNIQUE (session_id, section_id, subject_id),
    CONSTRAINT fk_ss_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id),
    CONSTRAINT fk_ss_section FOREIGN KEY (section_id) REFERENCES sections(id),
    CONSTRAINT fk_ss_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_ss_teacher FOREIGN KEY (teacher_id) REFERENCES staff(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Timetable (REQ-ACA-003: conflict prevention)
CREATE TABLE timetable_slots (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    session_id      BIGINT          NOT NULL,
    section_id      BIGINT          NOT NULL,
    subject_id      BIGINT          NOT NULL,
    teacher_id      BIGINT,
    day_of_week     TINYINT         NOT NULL COMMENT '1=Mon..7=Sun',
    start_time      TIME            NOT NULL,
    end_time        TIME            NOT NULL,
    room_no         VARCHAR(20),
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_timetable_slots PRIMARY KEY (id),
    -- Prevent: same teacher in two rooms at same time
    CONSTRAINT uq_tt_teacher_slot UNIQUE (session_id, teacher_id, day_of_week, start_time),
    -- Prevent: same section in two rooms at same time
    CONSTRAINT uq_tt_section_slot UNIQUE (session_id, section_id, day_of_week, start_time),
    CONSTRAINT fk_tt_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id),
    CONSTRAINT fk_tt_section FOREIGN KEY (section_id) REFERENCES sections(id),
    CONSTRAINT fk_tt_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_tt_teacher FOREIGN KEY (teacher_id) REFERENCES staff(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Homework (REQ-ACA-004)
CREATE TABLE homework (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    session_id      BIGINT          NOT NULL,
    section_id      BIGINT          NOT NULL,
    subject_id      BIGINT          NOT NULL,
    assigned_by     BIGINT          NOT NULL COMMENT 'Staff/teacher',
    title           VARCHAR(255)    NOT NULL,
    description     TEXT,
    due_date        DATE            NOT NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_homework PRIMARY KEY (id),
    CONSTRAINT fk_hw_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id),
    CONSTRAINT fk_hw_section FOREIGN KEY (section_id) REFERENCES sections(id),
    CONSTRAINT fk_hw_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_hw_staff FOREIGN KEY (assigned_by) REFERENCES staff(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Syllabus progress (REQ-ACA-005)
CREATE TABLE syllabus_progress (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    session_id      BIGINT          NOT NULL,
    section_id      BIGINT          NOT NULL,
    subject_id      BIGINT          NOT NULL,
    topic           VARCHAR(255)    NOT NULL,
    status          ENUM('PENDING','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'PENDING',
    completed_at    DATETIME(6),
    recorded_by     BIGINT          NOT NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_syllabus_progress PRIMARY KEY (id),
    CONSTRAINT fk_sp_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id),
    CONSTRAINT fk_sp_section FOREIGN KEY (section_id) REFERENCES sections(id),
    CONSTRAINT fk_sp_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_sp_staff FOREIGN KEY (recorded_by) REFERENCES staff(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- SECTION 4: ATTENDANCE (REQ-ATT-001..005)
-- ============================================================

CREATE TABLE attendance (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    student_id          BIGINT          NOT NULL,
    session_id          BIGINT          NOT NULL,
    section_id          BIGINT          NOT NULL,
    attendance_date     DATE            NOT NULL,
    status              ENUM('PRESENT','ABSENT','LATE','HALF_DAY','HOLIDAY','EXCUSED')
                        NOT NULL COMMENT 'REQ-ATT-001',
    marked_by           BIGINT          NOT NULL COMMENT 'Staff who marked',
    marked_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    remarks             VARCHAR(255),
    is_corrected        TINYINT(1)      NOT NULL DEFAULT 0,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_attendance PRIMARY KEY (id),
    -- REQ-ATT-002: Duplicate prevention — one record per student per day
    CONSTRAINT uq_attendance_student_date UNIQUE (student_id, attendance_date, session_id),
    CONSTRAINT fk_att_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_att_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id),
    CONSTRAINT fk_att_section FOREIGN KEY (section_id) REFERENCES sections(id),
    CONSTRAINT fk_att_marker FOREIGN KEY (marked_by) REFERENCES staff(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- REQ-ATT-003: Audit trail for attendance corrections
CREATE TABLE attendance_corrections (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    attendance_id   BIGINT          NOT NULL,
    old_status      VARCHAR(20)     NOT NULL,
    new_status      VARCHAR(20)     NOT NULL,
    reason          VARCHAR(500)    NOT NULL,
    corrected_by    BIGINT          NOT NULL,
    corrected_at    DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_att_corrections PRIMARY KEY (id),
    CONSTRAINT fk_ac_attendance FOREIGN KEY (attendance_id) REFERENCES attendance(id),
    CONSTRAINT fk_ac_user FOREIGN KEY (corrected_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Staff attendance
CREATE TABLE staff_attendance (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    staff_id        BIGINT          NOT NULL,
    attendance_date DATE            NOT NULL,
    status          ENUM('PRESENT','ABSENT','LATE','HALF_DAY','ON_LEAVE') NOT NULL,
    check_in_time   TIME,
    check_out_time  TIME,
    marked_by       BIGINT          NOT NULL,
    remarks         VARCHAR(255),
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_staff_attendance PRIMARY KEY (id),
    CONSTRAINT uq_staff_att_date UNIQUE (staff_id, attendance_date),
    CONSTRAINT fk_sa_staff FOREIGN KEY (staff_id) REFERENCES staff(id),
    CONSTRAINT fk_sa_marker FOREIGN KEY (marked_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- SECTION 5: FINANCE (REQ-FIN-001..009)
-- ============================================================

CREATE TABLE fee_categories (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100)    NOT NULL COMMENT 'Tuition/Transport/Hostel/etc.',
    description VARCHAR(255),
    is_active   TINYINT(1)      NOT NULL DEFAULT 1,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_fee_categories PRIMARY KEY (id),
    CONSTRAINT uq_fee_cat_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- REQ-FIN-001: Fee structures by session/class/category
CREATE TABLE fee_structures (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    session_id      BIGINT          NOT NULL,
    class_id        BIGINT          NOT NULL,
    category_id     BIGINT          NOT NULL,
    amount          DECIMAL(12,2)   NOT NULL COMMENT '>= 0',
    due_date        DATE,
    frequency       ENUM('ONE_TIME','MONTHLY','QUARTERLY','ANNUALLY') NOT NULL DEFAULT 'ONE_TIME',
    is_active       TINYINT(1)      NOT NULL DEFAULT 1,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by      BIGINT,
    CONSTRAINT pk_fee_structures PRIMARY KEY (id),
    CONSTRAINT chk_fee_amount CHECK (amount >= 0),
    CONSTRAINT fk_fs_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id),
    CONSTRAINT fk_fs_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_fs_category FOREIGN KEY (category_id) REFERENCES fee_categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- REQ-FIN-002: Student fee demands/dues
CREATE TABLE fee_demands (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    student_id      BIGINT          NOT NULL,
    fee_structure_id BIGINT         NOT NULL,
    session_id      BIGINT          NOT NULL,
    amount_due      DECIMAL(12,2)   NOT NULL COMMENT '>= 0',
    amount_paid     DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    discount_amount DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    due_date        DATE,
    status          ENUM('PENDING','PARTIAL','PAID','WAIVED') NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_fee_demands PRIMARY KEY (id),
    CONSTRAINT chk_demand_amount CHECK (amount_due >= 0),
    CONSTRAINT fk_fd_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_fd_fee_structure FOREIGN KEY (fee_structure_id) REFERENCES fee_structures(id),
    CONSTRAINT fk_fd_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- REQ-FIN-003: Payments and receipts (atomic write)
CREATE TABLE payments (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    student_id      BIGINT          NOT NULL,
    demand_id       BIGINT,
    session_id      BIGINT          NOT NULL,
    receipt_no      VARCHAR(50)     NOT NULL COMMENT 'Unique receipt number',
    reference_no    VARCHAR(80)     NOT NULL COMMENT 'Unique — REQ-FIN-004 idempotency',
    amount          DECIMAL(12,2)   NOT NULL COMMENT '> 0',
    payment_method  ENUM('CASH','UPI','CARD','CHEQUE','NEFT','RTGS','DD','OTHER') NOT NULL,
    payment_date    DATE            NOT NULL,
    status          ENUM('POSTED','REFUNDED','REVERSED') NOT NULL DEFAULT 'POSTED',
    remarks         VARCHAR(500),
    collected_by    BIGINT          NOT NULL COMMENT 'Cashier/accountant',
    refunded_by     BIGINT,
    refunded_at     DATETIME(6),
    refund_reason   VARCHAR(500),
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uq_payments_receipt_no UNIQUE (receipt_no),
    CONSTRAINT uq_payments_reference_no UNIQUE (reference_no),
    CONSTRAINT chk_payment_amount CHECK (amount > 0),
    CONSTRAINT fk_pay_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_pay_demand FOREIGN KEY (demand_id) REFERENCES fee_demands(id) ON DELETE SET NULL,
    CONSTRAINT fk_pay_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id),
    CONSTRAINT fk_pay_collector FOREIGN KEY (collected_by) REFERENCES users(id),
    CONSTRAINT fk_pay_refunder FOREIGN KEY (refunded_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- REQ-FIN-006: Discount/concession approval workflow
CREATE TABLE fee_discounts (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    student_id      BIGINT          NOT NULL,
    demand_id       BIGINT          NOT NULL,
    amount          DECIMAL(12,2)   NOT NULL,
    reason          VARCHAR(500)    NOT NULL,
    requested_by    BIGINT          NOT NULL,
    approval_status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    approved_by     BIGINT,
    approved_at     DATETIME(6),
    rejection_reason VARCHAR(500),
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_fee_discounts PRIMARY KEY (id),
    CONSTRAINT fk_disc_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_disc_demand FOREIGN KEY (demand_id) REFERENCES fee_demands(id),
    CONSTRAINT fk_disc_requester FOREIGN KEY (requested_by) REFERENCES users(id),
    CONSTRAINT fk_disc_approver FOREIGN KEY (approved_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- REQ-FIN-007: Expenses with approval state
CREATE TABLE expenses (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    category        VARCHAR(100)    NOT NULL,
    description     VARCHAR(500)    NOT NULL,
    amount          DECIMAL(12,2)   NOT NULL,
    expense_date    DATE            NOT NULL,
    vendor          VARCHAR(200),
    receipt_url     VARCHAR(1000),
    approval_status ENUM('DRAFT','PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'DRAFT',
    requested_by    BIGINT          NOT NULL,
    approved_by     BIGINT,
    approved_at     DATETIME(6),
    rejection_reason VARCHAR(500),
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_expenses PRIMARY KEY (id),
    CONSTRAINT chk_exp_amount CHECK (amount >= 0),
    CONSTRAINT fk_exp_requester FOREIGN KEY (requested_by) REFERENCES users(id),
    CONSTRAINT fk_exp_approver FOREIGN KEY (approved_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- SECTION 6: OPERATIONS (REQ-OPS-001..005)
-- ============================================================

-- REQ-OPS-001: Inventory
CREATE TABLE inventory_items (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(200)    NOT NULL,
    code            VARCHAR(50),
    category        VARCHAR(100),
    unit            VARCHAR(20)     NOT NULL DEFAULT 'PCS',
    current_stock   INT             NOT NULL DEFAULT 0,
    min_stock_level INT             NOT NULL DEFAULT 0,
    description     VARCHAR(500),
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_inventory_items PRIMARY KEY (id),
    CONSTRAINT uq_inv_code UNIQUE (code),
    CONSTRAINT chk_inv_stock CHECK (current_stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- REQ-OPS-002: Stock movements (source of truth for balance)
CREATE TABLE stock_movements (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    item_id         BIGINT          NOT NULL,
    movement_type   ENUM('IN','OUT','ADJUSTMENT') NOT NULL,
    quantity        INT             NOT NULL,
    reference_no    VARCHAR(80),
    remarks         VARCHAR(500),
    moved_by        BIGINT          NOT NULL,
    moved_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_stock_movements PRIMARY KEY (id),
    CONSTRAINT fk_sm_item FOREIGN KEY (item_id) REFERENCES inventory_items(id),
    CONSTRAINT fk_sm_user FOREIGN KEY (moved_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- REQ-OPS-003: Library
CREATE TABLE library_items (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    isbn            VARCHAR(20),
    title           VARCHAR(255)    NOT NULL,
    author          VARCHAR(200),
    publisher       VARCHAR(200),
    category        VARCHAR(100),
    total_copies    INT             NOT NULL DEFAULT 1,
    available_copies INT            NOT NULL DEFAULT 1,
    rack_location   VARCHAR(50),
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_library_items PRIMARY KEY (id),
    CONSTRAINT chk_lib_copies CHECK (available_copies >= 0 AND total_copies >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE library_issues (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    item_id         BIGINT          NOT NULL,
    borrower_type   ENUM('STUDENT','STAFF') NOT NULL,
    student_id      BIGINT,
    staff_id        BIGINT,
    issued_date     DATE            NOT NULL,
    due_date        DATE            NOT NULL,
    returned_date   DATE,
    fine_amount     DECIMAL(8,2)    NOT NULL DEFAULT 0.00,
    status          ENUM('ISSUED','RETURNED','OVERDUE') NOT NULL DEFAULT 'ISSUED',
    issued_by       BIGINT          NOT NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_library_issues PRIMARY KEY (id),
    CONSTRAINT fk_li_item FOREIGN KEY (item_id) REFERENCES library_items(id),
    CONSTRAINT fk_li_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE SET NULL,
    CONSTRAINT fk_li_staff FOREIGN KEY (staff_id) REFERENCES staff(id) ON DELETE SET NULL,
    CONSTRAINT fk_li_issuer FOREIGN KEY (issued_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- REQ-OPS-004: Hostel (prevents double-allocation via unique active constraint)
CREATE TABLE hostel_rooms (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    room_no         VARCHAR(20)     NOT NULL,
    floor           VARCHAR(10),
    block           VARCHAR(20),
    capacity        INT             NOT NULL DEFAULT 1,
    room_type       ENUM('SINGLE','DOUBLE','DORMITORY') NOT NULL DEFAULT 'SINGLE',
    is_active       TINYINT(1)      NOT NULL DEFAULT 1,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_hostel_rooms PRIMARY KEY (id),
    CONSTRAINT uq_hostel_room_no UNIQUE (room_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hostel_allocations (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    room_id         BIGINT          NOT NULL,
    student_id      BIGINT          NOT NULL,
    session_id      BIGINT          NOT NULL,
    bed_no          VARCHAR(10)     NOT NULL DEFAULT '1',
    allotted_from   DATE            NOT NULL,
    allotted_to     DATE,
    status          ENUM('ACTIVE','VACATED','TRANSFERRED') NOT NULL DEFAULT 'ACTIVE',
    allocated_by    BIGINT          NOT NULL,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_hostel_allocations PRIMARY KEY (id),
    -- REQ-OPS-004: No two active occupants in same bed
    CONSTRAINT uq_hostel_bed_active UNIQUE (room_id, bed_no, status, session_id),
    CONSTRAINT fk_ha_room FOREIGN KEY (room_id) REFERENCES hostel_rooms(id),
    CONSTRAINT fk_ha_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_ha_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id),
    CONSTRAINT fk_ha_allocator FOREIGN KEY (allocated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- REQ-OPS-005: Support Tickets
CREATE TABLE support_tickets (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    title           VARCHAR(255)    NOT NULL,
    description     TEXT            NOT NULL,
    category        VARCHAR(100),
    priority        ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'MEDIUM',
    status          ENUM('OPEN','IN_PROGRESS','RESOLVED','CLOSED','CANCELLED') NOT NULL DEFAULT 'OPEN',
    raised_by       BIGINT          NOT NULL,
    assigned_to     BIGINT,
    resolved_at     DATETIME(6),
    resolution_note TEXT,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_support_tickets PRIMARY KEY (id),
    CONSTRAINT fk_st_raiser FOREIGN KEY (raised_by) REFERENCES users(id),
    CONSTRAINT fk_st_assignee FOREIGN KEY (assigned_to) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ticket_comments (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    ticket_id   BIGINT          NOT NULL,
    comment     TEXT            NOT NULL,
    commented_by BIGINT         NOT NULL,
    created_at  DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ticket_comments PRIMARY KEY (id),
    CONSTRAINT fk_tc_ticket FOREIGN KEY (ticket_id) REFERENCES support_tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_tc_user FOREIGN KEY (commented_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- SECTION 7: AUDIT LOG (REQ-ADM-002, REQ-ADM-008, REQ-SEC-005)
-- ============================================================

CREATE TABLE audit_logs (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    actor_user_id   BIGINT          COMMENT 'NULL = system action',
    actor_username  VARCHAR(100),
    action          VARCHAR(100)    NOT NULL COMMENT 'e.g. USER_CREATED, FEE_COLLECTED',
    entity_type     VARCHAR(60)     NOT NULL COMMENT 'e.g. Student, Payment',
    entity_id       VARCHAR(50)     COMMENT 'Target entity PK',
    old_value       TEXT            COMMENT 'JSON snapshot of old state',
    new_value       TEXT            COMMENT 'JSON snapshot of new state',
    ip_address      VARCHAR(45),
    trace_id        VARCHAR(100),
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- SECTION 8: INDEXES (NFR-PERF-001, NFR-PERF-002)
-- ============================================================

-- Users
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_email ON users(email);

-- Students
CREATE INDEX idx_students_status ON students(status);
CREATE INDEX idx_students_admission_date ON students(admission_date);

-- Attendance  
CREATE INDEX idx_attendance_date ON attendance(attendance_date);
CREATE INDEX idx_attendance_student ON attendance(student_id);
CREATE INDEX idx_attendance_section_date ON attendance(section_id, attendance_date);

-- Payments
CREATE INDEX idx_payments_student ON payments(student_id);
CREATE INDEX idx_payments_date ON payments(payment_date);
CREATE INDEX idx_payments_status ON payments(status);

-- Fee demands
CREATE INDEX idx_fee_demands_student ON fee_demands(student_id);
CREATE INDEX idx_fee_demands_status ON fee_demands(status);
CREATE INDEX idx_fee_demands_session ON fee_demands(session_id);

-- Audit logs
CREATE INDEX idx_audit_actor ON audit_logs(actor_user_id);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_created ON audit_logs(created_at);
CREATE INDEX idx_audit_action ON audit_logs(action);

-- Library
CREATE INDEX idx_lib_issues_status ON library_issues(status);
CREATE INDEX idx_lib_issues_due ON library_issues(due_date);

-- Revoked tokens
CREATE INDEX idx_revoked_expires ON revoked_tokens(expires_at);

-- Support tickets
CREATE INDEX idx_tickets_status ON support_tickets(status);
CREATE INDEX idx_tickets_assignee ON support_tickets(assigned_to);

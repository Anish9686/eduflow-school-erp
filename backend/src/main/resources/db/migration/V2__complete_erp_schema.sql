-- V2 completes the requested ERP schema without changing the V1 baseline.
-- V1's section_subjects, timetable_slots, fee_discounts, hostel_rooms, and
-- hostel_allocations remain intact for compatibility with existing code/data.

CREATE TABLE admissions (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    application_no  VARCHAR(50)     NOT NULL,
    student_id      BIGINT,
    applicant_name  VARCHAR(200)    NOT NULL,
    applied_on      DATE            NOT NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    decision_at     DATETIME(6),
    decision_by     BIGINT,
    decision_note   VARCHAR(1000),
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_admissions PRIMARY KEY (id),
    CONSTRAINT uq_admissions_application_no UNIQUE (application_no),
    CONSTRAINT fk_admissions_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE SET NULL,
    CONSTRAINT fk_admissions_decision_by FOREIGN KEY (decision_by) REFERENCES users(id) ON DELETE SET NULL,
    KEY idx_admissions_status_applied (status, applied_on) COMMENT 'Admission review queue, newest applications first'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_history (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    student_id      BIGINT          NOT NULL,
    event_type      VARCHAR(50)     NOT NULL,
    previous_value  VARCHAR(500),
    new_value       VARCHAR(500),
    note            VARCHAR(1000),
    changed_by      BIGINT,
    changed_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_student_history PRIMARY KEY (id),
    CONSTRAINT fk_student_history_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_student_history_user FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE SET NULL,
    KEY idx_student_history_timeline (student_id, changed_at) COMMENT 'Student profile history in chronological order'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V1 section_subjects stores section-level subject/teacher assignment. This
-- class-level catalog supports class-wide subject offerings explicitly.
CREATE TABLE class_subjects (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    session_id      BIGINT          NOT NULL,
    class_id        BIGINT          NOT NULL,
    subject_id      BIGINT          NOT NULL,
    is_active       TINYINT(1)      NOT NULL DEFAULT 1,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_class_subjects PRIMARY KEY (id),
    CONSTRAINT uq_class_subject_session UNIQUE (session_id, class_id, subject_id),
    CONSTRAINT fk_class_subject_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id),
    CONSTRAINT fk_class_subject_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_class_subject_subject FOREIGN KEY (subject_id) REFERENCES subjects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE teacher_assignments (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    session_id      BIGINT          NOT NULL,
    section_id      BIGINT          NOT NULL,
    subject_id      BIGINT          NOT NULL,
    staff_id        BIGINT          NOT NULL,
    assigned_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    status          VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_teacher_assignments PRIMARY KEY (id),
    CONSTRAINT uq_teacher_assignment UNIQUE (session_id, section_id, subject_id, staff_id),
    CONSTRAINT fk_teacher_assignment_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id),
    CONSTRAINT fk_teacher_assignment_section FOREIGN KEY (section_id) REFERENCES sections(id),
    CONSTRAINT fk_teacher_assignment_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_teacher_assignment_staff FOREIGN KEY (staff_id) REFERENCES staff(id),
    KEY idx_teacher_assignments_staff_session (staff_id, session_id) COMMENT 'Find a staff member’s assignments for a session'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V1 timetable_slots remains the legacy schedule. This named table provides
-- the requested schema with a day/section lookup and duplicate-slot guards.
CREATE TABLE timetable (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    session_id      BIGINT          NOT NULL,
    class_id        BIGINT          NOT NULL,
    section_id      BIGINT          NOT NULL,
    subject_id      BIGINT          NOT NULL,
    teacher_id      BIGINT,
    day_of_week     TINYINT         NOT NULL,
    start_time      TIME            NOT NULL,
    end_time        TIME            NOT NULL,
    room_no         VARCHAR(20),
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_timetable PRIMARY KEY (id),
    CONSTRAINT chk_timetable_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_timetable_time CHECK (end_time > start_time),
    CONSTRAINT uq_timetable_section_slot UNIQUE (session_id, section_id, day_of_week, start_time),
    CONSTRAINT uq_timetable_teacher_slot UNIQUE (session_id, teacher_id, day_of_week, start_time),
    CONSTRAINT fk_timetable_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id),
    CONSTRAINT fk_timetable_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_timetable_section FOREIGN KEY (section_id) REFERENCES sections(id),
    CONSTRAINT fk_timetable_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_timetable_teacher FOREIGN KEY (teacher_id) REFERENCES staff(id) ON DELETE SET NULL,
    KEY idx_timetable_section_day (section_id, day_of_week, start_time) COMMENT 'Render one section’s weekly timetable in time order'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V1 fee_structures currently stores one category/amount per row; item rows
-- allow one structure to contain multiple fee components.
CREATE TABLE fee_structure_items (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    fee_structure_id    BIGINT          NOT NULL,
    category_id         BIGINT          NOT NULL,
    item_name           VARCHAR(100)    NOT NULL,
    amount              DECIMAL(12,2)   NOT NULL,
    due_date            DATE,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_fee_structure_items PRIMARY KEY (id),
    CONSTRAINT uq_fee_structure_item UNIQUE (fee_structure_id, category_id, item_name),
    CONSTRAINT chk_fee_structure_item_amount CHECK (amount >= 0),
    CONSTRAINT fk_fee_structure_item_structure FOREIGN KEY (fee_structure_id) REFERENCES fee_structures(id) ON DELETE CASCADE,
    CONSTRAINT fk_fee_structure_item_category FOREIGN KEY (category_id) REFERENCES fee_categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fee_demand_items (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    fee_demand_id           BIGINT          NOT NULL,
    fee_structure_item_id   BIGINT,
    description             VARCHAR(200)    NOT NULL,
    amount_due              DECIMAL(12,2)   NOT NULL,
    amount_paid             DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_fee_demand_items PRIMARY KEY (id),
    CONSTRAINT chk_fee_demand_item_amount CHECK (amount_due >= 0 AND amount_paid >= 0 AND amount_paid <= amount_due),
    CONSTRAINT fk_fee_demand_item_demand FOREIGN KEY (fee_demand_id) REFERENCES fee_demands(id) ON DELETE CASCADE,
    CONSTRAINT fk_fee_demand_item_structure FOREIGN KEY (fee_structure_item_id) REFERENCES fee_structure_items(id) ON DELETE SET NULL,
    KEY idx_fee_demand_items_demand (fee_demand_id) COMMENT 'Load the component lines for a fee demand'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V1 payments already enforce unique receipt_no/reference_no. Receipts adds
-- a one-to-one printable receipt record without altering existing payments.
CREATE TABLE receipts (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    payment_id      BIGINT          NOT NULL,
    receipt_number  VARCHAR(50)     NOT NULL,
    issued_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    issued_by       BIGINT,
    status          VARCHAR(30)     NOT NULL DEFAULT 'ISSUED',
    CONSTRAINT pk_receipts PRIMARY KEY (id),
    CONSTRAINT uq_receipts_payment UNIQUE (payment_id),
    CONSTRAINT uq_receipts_number UNIQUE (receipt_number),
    CONSTRAINT fk_receipts_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_receipts_issuer FOREIGN KEY (issued_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V1 fee_discounts contains approval workflow records; discounts is the
-- requested general-purpose discount ledger and does not replace that table.
CREATE TABLE discounts (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    student_id      BIGINT          NOT NULL,
    fee_demand_id   BIGINT,
    amount          DECIMAL(12,2)   NOT NULL,
    reason          VARCHAR(500)    NOT NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    approved_by     BIGINT,
    approved_at     DATETIME(6),
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_discounts PRIMARY KEY (id),
    CONSTRAINT chk_discounts_amount CHECK (amount >= 0),
    CONSTRAINT fk_discounts_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_discounts_demand FOREIGN KEY (fee_demand_id) REFERENCES fee_demands(id) ON DELETE SET NULL,
    CONSTRAINT fk_discounts_approver FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL,
    KEY idx_discounts_student_status (student_id, status) COMMENT 'Review discounts by student and approval state'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE suppliers (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    supplier_code   VARCHAR(50)     NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    contact_name    VARCHAR(150),
    email           VARCHAR(254),
    phone           VARCHAR(30),
    address         VARCHAR(1000),
    status          VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_suppliers PRIMARY KEY (id),
    CONSTRAINT uq_suppliers_code UNIQUE (supplier_code),
    KEY idx_suppliers_status_name (status, name) COMMENT 'List active suppliers in display order'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE application_settings (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    setting_key     VARCHAR(100)    NOT NULL,
    setting_value   TEXT            NOT NULL,
    description     VARCHAR(500),
    updated_by      BIGINT,
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_application_settings PRIMARY KEY (id),
    CONSTRAINT uq_application_settings_key UNIQUE (setting_key),
    CONSTRAINT fk_application_settings_user FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- MySQL has no partial unique indexes. A NULL generated guard leaves historic
-- non-active rows unrestricted while enforcing one ACTIVE occupant per bed.
ALTER TABLE hostel_allocations
    DROP INDEX uq_hostel_bed_active,
    ADD COLUMN active_bed_guard TINYINT
        GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY uq_hostel_active_bed_session (room_id, bed_no, session_id, active_bed_guard)
        COMMENT 'Prevent concurrent active occupants of one bed; retain any number of inactive history rows';

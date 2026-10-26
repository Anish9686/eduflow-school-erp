-- =========================================================================
-- V14: Results and Examination Management Module
-- =========================================================================

-- 1. Grading Schemes and Scale Rules
CREATE TABLE grading_schemes (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    name                VARCHAR(100)    NOT NULL,
    description         VARCHAR(255)    NULL,
    is_default          TINYINT(1)      NOT NULL DEFAULT 1,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_grading_schemes PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE grading_scale_rules (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    scheme_id           BIGINT          NOT NULL,
    grade               VARCHAR(10)     NOT NULL,
    min_percentage      DECIMAL(5,2)    NOT NULL,
    max_percentage      DECIMAL(5,2)    NOT NULL,
    grade_point         DECIMAL(4,2)    NULL,
    is_pass             TINYINT(1)      NOT NULL DEFAULT 1,
    remarks             VARCHAR(100)    NULL,
    order_seq           INT             NOT NULL DEFAULT 0,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_grading_scale_rules PRIMARY KEY (id),
    CONSTRAINT fk_gsr_scheme FOREIGN KEY (scheme_id) REFERENCES grading_schemes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Examinations
CREATE TABLE examinations (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    name                VARCHAR(150)    NOT NULL,
    exam_type           VARCHAR(50)     NOT NULL COMMENT 'UNIT_TEST, PERIODIC_ASSESSMENT, HALF_YEARLY, ANNUAL, PRACTICAL, FINAL',
    session_id          BIGINT          NOT NULL,
    class_id            BIGINT          NOT NULL,
    section_id          BIGINT          NULL COMMENT 'NULL means all sections in class',
    start_date          DATE            NOT NULL,
    end_date            DATE            NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED, SCHEDULED, ONGOING, COMPLETED, PUBLISHED, CANCELLED',
    grading_scheme_id   BIGINT          NULL,
    instructions        TEXT            NULL,
    published_at        DATETIME(6)     NULL,
    published_by        BIGINT          NULL,
    created_by          BIGINT          NOT NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_examinations PRIMARY KEY (id),
    CONSTRAINT fk_exam_session FOREIGN KEY (session_id) REFERENCES academic_sessions(id),
    CONSTRAINT fk_exam_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_exam_section FOREIGN KEY (section_id) REFERENCES sections(id) ON DELETE SET NULL,
    CONSTRAINT fk_exam_grading FOREIGN KEY (grading_scheme_id) REFERENCES grading_schemes(id) ON DELETE SET NULL,
    CONSTRAINT fk_exam_published_by FOREIGN KEY (published_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_exam_created_by FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_exams_session_class ON examinations (session_id, class_id, status);
CREATE INDEX idx_exams_dates ON examinations (start_date, end_date);

-- 3. Examination Schedules
CREATE TABLE exam_schedules (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    exam_id             BIGINT          NOT NULL,
    class_id            BIGINT          NOT NULL,
    section_id          BIGINT          NOT NULL,
    subject_id          BIGINT          NOT NULL,
    exam_date           DATE            NOT NULL,
    start_time          TIME            NOT NULL,
    end_time            TIME            NOT NULL,
    max_marks           DECIMAL(6,2)    NOT NULL DEFAULT 100.00,
    pass_marks          DECIMAL(6,2)    NOT NULL DEFAULT 35.00,
    room_no             VARCHAR(50)     NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_exam_schedules PRIMARY KEY (id),
    CONSTRAINT uq_exam_sec_sub UNIQUE (exam_id, section_id, subject_id),
    CONSTRAINT fk_esch_exam FOREIGN KEY (exam_id) REFERENCES examinations(id) ON DELETE CASCADE,
    CONSTRAINT fk_esch_class FOREIGN KEY (class_id) REFERENCES classes(id),
    CONSTRAINT fk_esch_section FOREIGN KEY (section_id) REFERENCES sections(id),
    CONSTRAINT fk_esch_subject FOREIGN KEY (subject_id) REFERENCES subjects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_esch_exam_date ON exam_schedules (exam_id, exam_date);

-- 4. Examination Marks Submissions (Teacher Workflow State)
CREATE TABLE exam_marks_submissions (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    exam_id             BIGINT          NOT NULL,
    section_id          BIGINT          NOT NULL,
    subject_id          BIGINT          NOT NULL,
    teacher_id          BIGINT          NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED',
    submitted_at        DATETIME(6)     NULL,
    submitted_by        BIGINT          NULL,
    reviewed_at         DATETIME(6)     NULL,
    reviewed_by         BIGINT          NULL,
    review_comments     TEXT            NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_exam_marks_submissions PRIMARY KEY (id),
    CONSTRAINT uq_subm_exam_sec_sub UNIQUE (exam_id, section_id, subject_id),
    CONSTRAINT fk_subm_exam FOREIGN KEY (exam_id) REFERENCES examinations(id) ON DELETE CASCADE,
    CONSTRAINT fk_subm_section FOREIGN KEY (section_id) REFERENCES sections(id),
    CONSTRAINT fk_subm_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_subm_teacher FOREIGN KEY (teacher_id) REFERENCES staff(id),
    CONSTRAINT fk_subm_submitted_by FOREIGN KEY (submitted_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_subm_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_subm_status ON exam_marks_submissions (status);

-- 5. Student Subject Exam Marks
CREATE TABLE student_exam_marks (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    exam_id             BIGINT          NOT NULL,
    schedule_id         BIGINT          NOT NULL,
    student_id          BIGINT          NOT NULL,
    subject_id          BIGINT          NOT NULL,
    marks_obtained      DECIMAL(6,2)    NULL,
    max_marks           DECIMAL(6,2)    NOT NULL,
    pass_marks          DECIMAL(6,2)    NOT NULL,
    percentage          DECIMAL(5,2)    NULL,
    grade               VARCHAR(10)     NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PRESENT' COMMENT 'PRESENT, ABSENT, EXEMPTED',
    teacher_remarks     VARCHAR(255)    NULL,
    created_by          BIGINT          NOT NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_student_exam_marks PRIMARY KEY (id),
    CONSTRAINT uq_sem_exam_student_subject UNIQUE (exam_id, student_id, subject_id),
    CONSTRAINT fk_sem_exam FOREIGN KEY (exam_id) REFERENCES examinations(id) ON DELETE CASCADE,
    CONSTRAINT fk_sem_schedule FOREIGN KEY (schedule_id) REFERENCES exam_schedules(id) ON DELETE CASCADE,
    CONSTRAINT fk_sem_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_sem_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_sem_created_by FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_sem_exam_student ON student_exam_marks (exam_id, student_id);

-- 6. Overall Student Exam Results (Aggregated and Published State)
CREATE TABLE student_exam_results (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    exam_id             BIGINT          NOT NULL,
    student_id          BIGINT          NOT NULL,
    total_max_marks     DECIMAL(8,2)    NOT NULL,
    total_marks_obtained DECIMAL(8,2)   NOT NULL,
    percentage          DECIMAL(5,2)    NOT NULL,
    overall_grade       VARCHAR(10)     NULL,
    gpa                 DECIMAL(4,2)    NULL,
    result_status       VARCHAR(20)     NOT NULL COMMENT 'PASS, FAIL, WITHHELD, INCOMPLETE',
    rank_in_class       INT             NULL,
    rank_in_section     INT             NULL,
    remarks             VARCHAR(255)    NULL,
    is_published        TINYINT(1)      NOT NULL DEFAULT 0,
    published_at        DATETIME(6)     NULL,
    calculated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_student_exam_results PRIMARY KEY (id),
    CONSTRAINT uq_ser_exam_student UNIQUE (exam_id, student_id),
    CONSTRAINT fk_ser_exam FOREIGN KEY (exam_id) REFERENCES examinations(id) ON DELETE CASCADE,
    CONSTRAINT fk_ser_student FOREIGN KEY (student_id) REFERENCES students(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ser_published ON student_exam_results (exam_id, is_published, student_id);

-- 7. Audit Corrections for Published Results
CREATE TABLE exam_result_corrections (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    exam_id             BIGINT          NOT NULL,
    student_id          BIGINT          NOT NULL,
    subject_id          BIGINT          NOT NULL,
    previous_marks      DECIMAL(6,2)    NULL,
    new_marks           DECIMAL(6,2)    NOT NULL,
    previous_status     VARCHAR(20)     NULL,
    new_status          VARCHAR(20)     NOT NULL,
    reason              TEXT            NOT NULL,
    corrected_by        BIGINT          NOT NULL,
    corrected_at        DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_exam_result_corrections PRIMARY KEY (id),
    CONSTRAINT fk_erc_exam FOREIGN KEY (exam_id) REFERENCES examinations(id) ON DELETE CASCADE,
    CONSTRAINT fk_erc_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_erc_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_erc_corrected_by FOREIGN KEY (corrected_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_erc_exam_student ON exam_result_corrections (exam_id, student_id);

-- 8. Default Grading Scheme Seed Data
INSERT INTO grading_schemes (id, name, description, is_default) VALUES
    (1, 'Standard 10-Point Scale', 'Standard percentage to letter-grade scale with 10-point GPA scale', 1);

INSERT INTO grading_scale_rules (scheme_id, grade, min_percentage, max_percentage, grade_point, is_pass, remarks, order_seq) VALUES
    (1, 'A+', 90.00, 100.00, 10.0, 1, 'Outstanding', 1),
    (1, 'A',  80.00,  89.99,  9.0, 1, 'Excellent', 2),
    (1, 'B+', 70.00,  79.99,  8.0, 1, 'Very Good', 3),
    (1, 'B',  60.00,  69.99,  7.0, 1, 'Good', 4),
    (1, 'C',  50.00,  59.99,  6.0, 1, 'Satisfactory', 5),
    (1, 'D',  35.00,  49.99,  4.0, 1, 'Pass', 6),
    (1, 'F',   0.00,  34.99,  0.0, 0, 'Fail', 7);

-- 9. Permissions Catalog for Examination & Results
INSERT INTO permissions (code, description, module) VALUES
    ('EXAM:READ', 'Read examination details and schedules', 'EXAM'),
    ('EXAM:CREATE', 'Create examinations', 'EXAM'),
    ('EXAM:UPDATE', 'Update examinations and status', 'EXAM'),
    ('EXAM:DELETE', 'Delete examinations without dependent marks', 'EXAM'),
    ('EXAM:SCHEDULE', 'Configure examination schedules', 'EXAM'),
    ('RESULT:READ', 'View examination marks and results in permitted scope', 'RESULT'),
    ('RESULT:CREATE', 'Enter draft examination marks for assigned subjects', 'RESULT'),
    ('RESULT:UPDATE', 'Update draft examination marks for assigned subjects', 'RESULT'),
    ('RESULT:SUBMIT', 'Submit marks for administrative review', 'RESULT'),
    ('RESULT:REVIEW', 'Review and decide submitted examination marks', 'RESULT'),
    ('RESULT:APPROVE', 'Approve submitted examination marks', 'RESULT'),
    ('RESULT:PUBLISH', 'Publish final examination results and generate report cards', 'RESULT'),
    ('RESULT:UNPUBLISH', 'Unpublish published examination results with audit', 'RESULT'),
    ('RESULT:REPORT:READ', 'Read published report cards in permitted scope', 'RESULT'),
    ('RESULT:REPORT:EXPORT', 'Export result summaries and report cards', 'RESULT');

-- 10. Role-Permission Grants Baseline
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (
    -- SUPER_ADMIN: Full access
    SELECT 'SUPER_ADMIN' role_code, 'EXAM:READ' permission_code UNION ALL
    SELECT 'SUPER_ADMIN','EXAM:CREATE' UNION ALL SELECT 'SUPER_ADMIN','EXAM:UPDATE' UNION ALL
    SELECT 'SUPER_ADMIN','EXAM:DELETE' UNION ALL SELECT 'SUPER_ADMIN','EXAM:SCHEDULE' UNION ALL
    SELECT 'SUPER_ADMIN','RESULT:READ' UNION ALL SELECT 'SUPER_ADMIN','RESULT:CREATE' UNION ALL
    SELECT 'SUPER_ADMIN','RESULT:UPDATE' UNION ALL SELECT 'SUPER_ADMIN','RESULT:SUBMIT' UNION ALL
    SELECT 'SUPER_ADMIN','RESULT:REVIEW' UNION ALL SELECT 'SUPER_ADMIN','RESULT:APPROVE' UNION ALL
    SELECT 'SUPER_ADMIN','RESULT:PUBLISH' UNION ALL SELECT 'SUPER_ADMIN','RESULT:UNPUBLISH' UNION ALL
    SELECT 'SUPER_ADMIN','RESULT:REPORT:READ' UNION ALL SELECT 'SUPER_ADMIN','RESULT:REPORT:EXPORT' UNION ALL

    -- ADMIN: Full operational management
    SELECT 'ADMIN','EXAM:READ' UNION ALL SELECT 'ADMIN','EXAM:CREATE' UNION ALL
    SELECT 'ADMIN','EXAM:UPDATE' UNION ALL SELECT 'ADMIN','EXAM:SCHEDULE' UNION ALL
    SELECT 'ADMIN','RESULT:READ' UNION ALL SELECT 'ADMIN','RESULT:CREATE' UNION ALL
    SELECT 'ADMIN','RESULT:UPDATE' UNION ALL SELECT 'ADMIN','RESULT:SUBMIT' UNION ALL
    SELECT 'ADMIN','RESULT:REVIEW' UNION ALL SELECT 'ADMIN','RESULT:APPROVE' UNION ALL
    SELECT 'ADMIN','RESULT:PUBLISH' UNION ALL SELECT 'ADMIN','RESULT:UNPUBLISH' UNION ALL
    SELECT 'ADMIN','RESULT:REPORT:READ' UNION ALL SELECT 'ADMIN','RESULT:REPORT:EXPORT' UNION ALL

    -- MANAGEMENT: Read-only access
    SELECT 'MANAGEMENT','EXAM:READ' UNION ALL SELECT 'MANAGEMENT','RESULT:READ' UNION ALL
    SELECT 'MANAGEMENT','RESULT:REPORT:READ' UNION ALL SELECT 'MANAGEMENT','RESULT:REPORT:EXPORT' UNION ALL

    -- TEACHER: Assigned class/subject marks entry & submission
    SELECT 'TEACHER','EXAM:READ' UNION ALL SELECT 'TEACHER','RESULT:READ' UNION ALL
    SELECT 'TEACHER','RESULT:CREATE' UNION ALL SELECT 'TEACHER','RESULT:UPDATE' UNION ALL
    SELECT 'TEACHER','RESULT:SUBMIT' UNION ALL SELECT 'TEACHER','RESULT:REPORT:READ' UNION ALL

    -- PARENT_GUARDIAN: Read-only published results for linked children
    SELECT 'PARENT_GUARDIAN','RESULT:READ' UNION ALL SELECT 'PARENT_GUARDIAN','RESULT:REPORT:READ' UNION ALL

    -- STUDENT: Read-only published results for self
    SELECT 'STUDENT','RESULT:READ' UNION ALL SELECT 'STUDENT','RESULT:REPORT:READ'
) baseline
JOIN roles r ON r.code = baseline.role_code
JOIN permissions p ON p.code = baseline.permission_code
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions existing
    WHERE existing.role_id = r.id AND existing.permission_id = p.id
);

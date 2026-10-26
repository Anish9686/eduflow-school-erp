-- Link portal accounts to parent/guardian records so guardian reads can be scoped.
ALTER TABLE guardians
    ADD COLUMN user_id BIGINT NULL AFTER email,
    ADD CONSTRAINT uq_guardians_user UNIQUE (user_id),
    ADD CONSTRAINT fk_guardians_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

-- Support guardian-to-student scope checks and per-student document retrieval.
CREATE INDEX idx_student_guardians_guardian_student
    ON student_guardians (guardian_id, student_id);
-- Student self-service queries start with the linked portal account id.
CREATE INDEX idx_students_user_id ON students (user_id);
-- Documents are listed newest-first within a student profile.
CREATE INDEX idx_student_documents_student_uploaded
    ON student_documents (student_id, uploaded_at);

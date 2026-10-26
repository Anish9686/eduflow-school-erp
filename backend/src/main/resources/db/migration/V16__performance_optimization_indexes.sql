-- V16: Performance optimization indexes
-- Evidence: Student directory listing, sorting, and reporting default to ORDER BY created_at DESC with optional status filter.
-- At 100,000 students (NFR-CAP-002), without these indexes MySQL performs full table scans and filesort.

CREATE INDEX idx_students_status_created_at ON students(status, created_at);
CREATE INDEX idx_students_created_at ON students(created_at);

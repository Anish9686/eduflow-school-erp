# Student and Admission module

## Index rationale

- `uq_students_admission_no` enforces the requirement that each student admission number is unique and supports exact admission number lookups.
- `uq_admissions_application_no` prevents duplicate application references and supports exact application lookup.
- `idx_students_status_admission_date` supports status-filtered student views ordered by admission date.
- `idx_student_history_timeline (student_id, changed_at)` supports loading one student's history newest-first.
- `idx_admissions_status_applied (status, applied_on)` supports admission review queues filtered by state and ordered by application date.
- `uq_guardians_user` ensures one guardian profile is linked to at most one portal account. Nullable values allow guardian records without portal access.
- `idx_student_guardians_guardian_student (guardian_id, student_id)` supports finding a guardian's linked students for parent scope checks. The existing primary key starts with `student_id`, so it does not serve this lookup direction.
- `idx_students_user_id (user_id)` supports student self-service scope checks by portal account.
- `idx_student_documents_student_uploaded (student_id, uploaded_at)` supports loading one student's documents newest-first.

Indexes declared in V1/V2 are unchanged. V4 adds guardian-to-account linkage and only the indexes required for account scope and per-student document access.

## API surface

- `GET/POST /api/v1/students`, `GET/PUT /api/v1/students/{id}`
- `GET/POST/PUT/DELETE /api/v1/students/{id}/guardians`
- `GET/POST /api/v1/students/{id}/documents`, and `GET /api/v1/students/{id}/documents/{documentId}/content`
- `POST /api/v1/students/{id}/transfer`, `POST /api/v1/students/{id}/exit`, and `GET /api/v1/students/{id}/history`
- `GET/POST /api/v1/admissions`, `GET /api/v1/admissions/{id}`, `POST /api/v1/admissions/{id}/submit`, and `PATCH /api/v1/admissions/{id}/status`

Every endpoint uses the `STUDENT:READ`, `STUDENT:CREATE`, or `STUDENT:UPDATE` permission as appropriate. Student and guardian portal accounts are scoped to records linked by `students.user_id` and `guardians.user_id`; staff access remains controlled by backend permissions.

Admission applications require a birth certificate before moving from draft to pending. Decisions follow an explicit state transition map. Transfers and exits write a history event in the same transaction as the status change. Document uploads are limited to PDF/JPEG/PNG, 10 MiB, and validated against both declared MIME type and file signature.

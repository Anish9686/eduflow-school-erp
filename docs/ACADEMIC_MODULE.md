# Academic module

Academic entities already exist in V1/V2 migrations. V5 adds only the academic permission catalog and query indexes; existing migrations are unchanged.

## Index rationale (V5)

- `idx_homework_session_section_due (session_id, section_id, due_date)` supports session/section homework lists ordered by the due date.
- `idx_syllabus_scope (session_id, section_id, subject_id, status)` supports filtered progress views for a class section and subject.
- `idx_timetable_teacher_day (teacher_id, session_id, day_of_week, start_time)` supports a teacher's weekly timetable.

V1/V2 already provide uniqueness for class names, class-section names, subject codes, class-subject-session assignments, and teacher assignments. `idx_teacher_assignments_staff_session` supports teacher scope checks; `idx_timetable_section_day` supports the section timetable.

## API groups

All paths use `/api/v1/academics`:

- Sessions: list, create, update and activate/deactivate.
- Classes, sections and subjects: list/create/update; sections must belong to an existing class, and `(class_id, name)` remains unique.
- Class-subject and teacher assignments: create/list; teacher assignments require a TEACHER-linked active staff profile and an active class-subject assignment for the selected session.
- Timetable: list/create/update/delete. The service checks both overlapping section and teacher periods in a transaction, serializing timetable writes on the academic-session row. Existing database unique constraints also prevent duplicate exact start slots.
- Homework: list/create/delete. A TEACHER can create only for an active exact session/section/subject assignment and can delete only their own items. `HOMEWORK:MANAGE` permits staff-wide management.
- Syllabus: list/create/update. Teacher updates require a matching active teaching assignment. Creation records the staff actor; all changes create audit entries and update the timestamp.

Permissions are seeded by V5. `SUPER_ADMIN` receives the new permissions; other role grants must be assigned through the existing audited RBAC administration API. Teachers who use academic lookup screens need `ACADEMIC:READ` in addition to the specific homework, timetable, or syllabus permission.

## Operating note

The academic schema references `staff.user_id` and `user_roles` to identify teacher accounts. Staff records must be linked to a user with the `TEACHER` role before they can receive academic teaching assignments.

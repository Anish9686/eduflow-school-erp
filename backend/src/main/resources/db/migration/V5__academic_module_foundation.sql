-- Permission catalog for the academic module. Non-super-admin role grants
-- remain an explicit audited RBAC administration action.
INSERT INTO permissions (code, description, module) VALUES
    ('ACADEMIC:READ', 'Read academic setup and teaching plans', 'ACADEMIC'),
    ('ACADEMIC:MANAGE', 'Manage sessions, classes, sections, subjects and assignments', 'ACADEMIC'),
    ('TIMETABLE:READ', 'Read timetable in permitted teaching scope', 'TIMETABLE'),
    ('TIMETABLE:MANAGE', 'Manage academic timetable entries', 'TIMETABLE'),
    ('HOMEWORK:READ', 'Read homework in permitted teaching scope', 'HOMEWORK'),
    ('HOMEWORK:CREATE', 'Create homework in assigned teaching scope', 'HOMEWORK'),
    ('HOMEWORK:MANAGE', 'Manage homework across academic scopes', 'HOMEWORK'),
    ('SYLLABUS:READ', 'Read syllabus progress in permitted scope', 'SYLLABUS'),
    ('SYLLABUS:UPDATE', 'Record syllabus progress in assigned teaching scope', 'SYLLABUS'),
    ('SYLLABUS:MANAGE', 'Manage syllabus progress across academic scopes', 'SYLLABUS');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'SUPER_ADMIN'
  AND p.code IN ('ACADEMIC:READ','ACADEMIC:MANAGE','TIMETABLE:READ','TIMETABLE:MANAGE',
                 'HOMEWORK:READ','HOMEWORK:CREATE','HOMEWORK:MANAGE',
                 'SYLLABUS:READ','SYLLABUS:UPDATE','SYLLABUS:MANAGE');

-- Queue and profile access paths used by the academic UI.
CREATE INDEX idx_homework_session_section_due
    ON homework (session_id, section_id, due_date);
CREATE INDEX idx_syllabus_scope
    ON syllabus_progress (session_id, section_id, subject_id, status);
CREATE INDEX idx_timetable_teacher_day
    ON timetable (teacher_id, session_id, day_of_week, start_time);
